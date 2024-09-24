package org.gosulang.plexus.compiler.gosu;

import gw.internal.ext.com.beust.jcommander.JCommander;
import gw.lang.gosuc.GosucUtil;
import gw.lang.gosuc.cli.CommandLineCompiler;
import gw.lang.gosuc.cli.CommandLineOptions;
import gw.lang.gosuc.simple.SoutCompilerDriver;
import java.net.URI;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import org.codehaus.plexus.compiler.AbstractCompiler;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerMessage;
import org.codehaus.plexus.compiler.CompilerOutputStyle;
import org.codehaus.plexus.compiler.CompilerResult;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.codehaus.plexus.compiler.CompilerMessage.Kind.ERROR;
import static org.codehaus.plexus.compiler.CompilerMessage.Kind.WARNING;

public class GosuCompiler extends AbstractCompiler {

  public GosuCompiler() {
    super(CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE, "", ".class", null); // see MCOMPILER-199, mentioned in AbstractCompileMojo#getCompileSources.  It appears the empty string is the only workaround to have more than one static file suffix.
  }

  @Override
  public String[] createCommandLine(CompilerConfiguration config) throws CompilerException {
    return new String[0];
  }

  @Override
  public String getCompilerId() {
    return null;
  }

  @Override
  public CompilerResult performCompile(CompilerConfiguration config) throws CompilerException {

    File destinationDir = new File(config.getOutputLocation());

    if (!destinationDir.exists()) {
      destinationDir.mkdirs();
    }

    String[] sourceFiles = getSourceFiles(config);

    if ((sourceFiles == null) || (sourceFiles.length == 0)) {
      return new CompilerResult(); //defaults to 'success == true'
    }

    if ((getLogger() != null) && getLogger().isInfoEnabled()) {
      getLogger().info("Compiling " + sourceFiles.length + " " +
          "source file" + (sourceFiles.length == 1 ? "" : "s") +
          " to " + destinationDir.getAbsolutePath());
    }

    CompilerResult result;

    if (config.isFork()) {
      String executable = config.getExecutable();

      if (StringUtils.isEmpty(executable)) {
        try {
          executable = getJavaExecutable();
        } catch (IOException e) {
          getLogger().warn("Unable to autodetect 'java' path, using 'java' from the environment.");
          executable = "java";
        }
      }      

      config.setExecutable(executable);
      
      result = compileOutOfProcess(config);
    } else {
      result = compileInProcess(config);
    }

    return result;
  }

  CompilerResult compileOutOfProcess(CompilerConfiguration config) throws CompilerException {
    Commandline cli = new Commandline();
    cli.setWorkingDirectory(config.getWorkingDirectory().getAbsolutePath());
    cli.setExecutable(config.getExecutable());

    //respect JAVA_OPTS, if it exists
    String JAVA_OPTS = System.getenv("JAVA_OPTS");
    if(JAVA_OPTS != null) {
      cli.addArguments(new String[] {JAVA_OPTS});
    }

    if(!StringUtils.isEmpty(config.getMeminitial())) {
      cli.addArguments(new String[] {"-Xms".concat(config.getMeminitial())});
    }

    if(!StringUtils.isEmpty(config.getMaxmem())) {
      cli.addArguments(new String[] {"-Xmx".concat(config.getMaxmem())});
    }

    //compilerArgs - arguments to send to the forked JVM
    Set<String> compilerArgs = config.getCustomCompilerArgumentsAsMap().keySet();
    if(compilerArgs.size() > 0) {
      cli.addArguments(compilerArgs.toArray(new String[(compilerArgs.size())]));
    }

    if(Os.isFamily(Os.FAMILY_MAC)) {
      cli.addArguments(new String[] {"-Xdock:name=Gosuc"});
    }

    getLogger().info("Initializing gosuc compiler");
    cli.addArguments( new String[] {"-classpath",
      String.join( File.pathSeparator, GosucUtil.getGosuBootstrapJars().stream()
        .map( uri -> new File( URI.create( uri ) ).getAbsolutePath() ).collect( Collectors.toList() ) ) } );

    cli.addArguments(new String[] {"gw.lang.gosuc.cli.CommandLineCompiler"});

    try {
      File argFile = createArgFile(config);
      cli.addArguments(new String[] { "@" + argFile.getCanonicalPath().replace(File.separatorChar, '/') });
    } catch (IOException e) {
      throw new CompilerException("Error creating argfile with gosuc arguments", e);
    }

    CommandLineUtils.StringStreamConsumer sysout = new CommandLineUtils.StringStreamConsumer();
    CommandLineUtils.StringStreamConsumer syserr = new CommandLineUtils.StringStreamConsumer();
    
    int exitCode;
    
    try {
      if(config.isVerbose()) {
        getLogger().info("Executing gosuc in external process with command: " + cli.toString());
      }
      exitCode = CommandLineUtils.executeCommandLine(cli, sysout, syserr);
    } catch (CommandLineException e) {
      throw new CompilerException("Error while executing the external compiler.", e);
    }

    List<CompilerMessage> messages = parseMessages(exitCode, sysout.getOutput());

    String err = syserr.getOutput();
    if( err != null && !err.isEmpty() )
    {
      getLogger().info("Errors:\n" + err);
    }

    int warningCt = 0;
    int errorCt = 0;
    for(CompilerMessage message : messages) {
      switch(message.getKind()) {
        case WARNING:
          warningCt++;
          break;
        case ERROR:
          errorCt++;
          break;
      }
    }

    if(config.isShowWarnings()) {
      getLogger().info(String.format("gosuc completed with %d warnings and %d errors.", warningCt, errorCt));
    } else {
      getLogger().info(String.format("gosuc completed with %d errors. Warnings were disabled.", errorCt));
    }

    return new CompilerResult(exitCode == 0, messages);
  }

  CompilerResult compileInProcess( CompilerConfiguration config ) throws CompilerException
  {
    List<String> cli = new ArrayList<>();
    File workingDirectory = config.getWorkingDirectory();
    if( workingDirectory != null )
    {
      System.setProperty( "user.dir", workingDirectory.getAbsolutePath() );
    }

    getLogger().info( "Initializing gosuc compiler" );

    addGosucArgs( config, cli );

    CommandLineOptions options = new CommandLineOptions();
    new JCommander( options, cli.toArray( new String[cli.size()] ) );
    options.setSourceFiles( Arrays.asList( getSourceFiles( config ) ) );
    SoutCompilerDriver driver = new SoutCompilerDriver( true, !options.isNoWarn() );
    boolean thresholdExceeded = CommandLineCompiler.invoke( options, driver );

    if( config.isShowWarnings() )
    {
      getLogger().info( String.format( "gosuc completed with %d warnings and %d errors.", driver.getWarnings().size(), driver.getErrors().size() ) );
    }
    else
    {
      getLogger().info( String.format( "gosuc completed with %d errors. Warnings were disabled.", driver.getErrors().size() ) );
    }

    List<CompilerMessage> messages = driver.getWarnings().stream().map( warningMsg -> new CompilerMessage( warningMsg, WARNING ) ).collect( Collectors.toList() );
    if( driver.hasErrors() )
    {
      messages.addAll( driver.getErrors().stream().map( errorMsg -> new CompilerMessage( errorMsg, ERROR ) ).collect( Collectors.toList() ) );
    }
    return new CompilerResult( !thresholdExceeded, messages );
  }

  private File createArgFile(CompilerConfiguration config) throws IOException {
    File tempFile;
    if ((getLogger() != null) && getLogger().isDebugEnabled()) {
      tempFile = File.createTempFile(CommandLineCompiler.class.getName(), "arguments", new File(config.getOutputLocation()));
    } else {
      tempFile = File.createTempFile(CommandLineCompiler.class.getName(), "arguments");
      tempFile.deleteOnExit();
    }

    List<String> fileOutput = new ArrayList<>();
    addGosucArgs( config, fileOutput );

    for(File sourceFile : config.getSourceFiles()) {
      fileOutput.add(sourceFile.getPath());
    }

    Files.write(tempFile.toPath(), fileOutput, StandardCharsets.UTF_8);

    return tempFile;
  }

  private void addGosucArgs( CompilerConfiguration config, List<String> fileOutput )
  {
    // The classpath used to initialize Gosu; CommandLineCompiler will supplement this with the JRE jars
    fileOutput.add("-classpath");
    fileOutput.add(String.join( File.pathSeparator, config.getClasspathEntries()));

    fileOutput.add("-d");
    fileOutput.add(config.getOutputLocation());

    fileOutput.add("-sourcepath");
    fileOutput.add(String.join(File.pathSeparator, config.getSourceLocations()));

    if(!config.isShowWarnings()) {
      fileOutput.add("-nowarn");
    }

    if(config.isVerbose()) {
      fileOutput.add("-verbose");
    }
  }

  private List<CompilerMessage> parseMessages(int exitCode, String sysout) {
    List<CompilerMessage> messages = new ArrayList<>();

    String error = "] error:";
    String warning = "] warning: ";
    for( StringTokenizer tokenizer = new StringTokenizer( sysout, "\r\n" ); tokenizer.hasMoreTokens(); )
    {
      String line = tokenizer.nextToken();
      boolean bError;
      if( (bError = line.contains( error )) || line.contains( warning ) )
      {
        StringBuilder msg = new StringBuilder( line );
        for( int i = 0; i < 3 && tokenizer.hasMoreTokens(); i++ )
        {
          msg.append( "\n" ).append( tokenizer.nextToken() );
        }
        messages.add( new CompilerMessage( msg.toString(), bError ? ERROR : WARNING ) );
      }
    }
    return messages;
  }

  /**
   * Get the path of the java executable: try to find it depending the
   * OS or the <code>java.home</code> system property or the
   * <code>JAVA_HOME</code> environment variable.
   *
   * @return the absolute path of the java executable
   * @throws IOException
   *             if not found
   */
  private String getJavaExecutable() throws IOException {
    String javaCommand = "java" + (Os.isFamily(Os.FAMILY_WINDOWS) ? ".exe" : "");

    String javaHome = System.getProperty("java.home");
    File javaExe;
    if (Os.isName("AIX")) {
      javaExe = new File(javaHome + File.separator + ".." + File.separator + "sh", javaCommand);
    } else if (Os.isName("Mac OS X")) {
      javaExe = new File(javaHome + File.separator + "bin", javaCommand);
    } else {
      javaExe = new File(javaHome + File.separator + ".." + File.separator + "bin", javaCommand);
    }

    // ----------------------------------------------------------------------
    // Try to find javaExe from JAVA_HOME environment variable
    // ----------------------------------------------------------------------
    if (!javaExe.isFile()) {
      Properties env = CommandLineUtils.getSystemEnvVars();
      javaHome = env.getProperty("JAVA_HOME");
      if (StringUtils.isEmpty(javaHome)) {
        throw new IOException("The environment variable JAVA_HOME is not correctly set.");
      }
      if (!new File(javaHome).isDirectory()) {
        throw new IOException("The environment variable JAVA_HOME=" + javaHome
            + " doesn't exist or is not a valid directory.");
      }

      javaExe = new File(env.getProperty("JAVA_HOME") + File.separator + "bin", javaCommand);
    }

    if (!javaExe.isFile()) {
      throw new IOException("The java executable '" + javaExe
          + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable.");
    }

    return javaExe.getAbsolutePath();
  }

}
