package gw.lang.gosuc.simple;

import gw.config.CommonServices;
import gw.config.ExecutionMode;
import gw.config.IMemoryMonitor;
import gw.config.IPlatformHelper;
import gw.config.Registry;
import gw.fs.FileFactory;
import gw.fs.IDirectory;
import gw.fs.IFile;
import gw.lang.gosuc.GosucDependency;
import gw.lang.gosuc.GosucModule;
import gw.lang.gosuc.cli.CommandLineOptions;
import gw.lang.init.GosuInitialization;
import gw.lang.javac.SourceJavaFileObject;
import gw.lang.parser.GosuParserFactory;
import gw.lang.parser.ICoercionManager;
import gw.lang.parser.IParseIssue;
import gw.lang.parser.IParsedElement;
import gw.lang.parser.exceptions.ParseWarning;
import gw.lang.parser.statements.IClassFileStatement;
import gw.lang.parser.statements.IClassStatement;
import gw.lang.reflect.IEntityAccess;
import gw.lang.reflect.IType;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.gs.IGosuClass;
import gw.lang.reflect.gs.ISourceFileHandle;
import gw.lang.reflect.java.IJavaType;
import gw.lang.reflect.module.IExecutionEnvironment;
import gw.lang.reflect.module.IFileSystem;
import gw.lang.reflect.module.IModule;
import gw.util.PathUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import manifold.internal.javac.IJavaParser;
import manifold.internal.javac.InMemoryClassJavaFileObject;
import manifold.util.NecessaryEvilUtil;
import manifold.util.ReflectUtil;


import static gw.lang.gosuc.simple.ICompilerDriver.ERROR;
import static gw.lang.gosuc.simple.ICompilerDriver.WARNING;

public class GosuCompiler implements IGosuCompiler
{
  /**
   * EXPERIMENTAL: If true, read source files and tokenize asynchronously ahead of compilation via ThreadPoolExecutor.
   * This is a CPU perf optimization.
   */
  public static boolean ASYNC_TOKENIZATION = true;

  private static final String[] SOURCE_EXTS = { ".gs", ".gsx", ".gst", ".java" };

  protected GosuInitialization _gosuInitialization;
  protected File _compilingSourceFile;
  private ThreadPoolExecutor _taskQueue;

  @Override
  public boolean compile( CommandLineOptions options, ICompilerDriver driver )
  {
    _taskQueue = makeTaskQueue();

    List<String> gosuFiles = new ArrayList<>();
    List<String> javaFiles = new ArrayList<>();
    for( String fileName : getSourceFiles( options ) )
    {
      if( fileName.toLowerCase().endsWith( ".java" ) )
      {
        javaFiles.add( fileName );
      }
      else
      {
        gosuFiles.add( fileName );
      }
    }

    if( !gosuFiles.isEmpty() )
    {
      if( compileGosuSources( options, driver, gosuFiles ) )
      {
        return true;
      }
    }

    if( !javaFiles.isEmpty() )
    {
      if( compileJavaSources( options, driver, javaFiles ) )
      {
        return true;
      }
    }
    return false;
  }

  private ThreadPoolExecutor makeTaskQueue()
  {
    int cores = Runtime.getRuntime().availableProcessors() - 1;
    int threads = Math.max( cores, 1 );
    return (ThreadPoolExecutor)Executors.newFixedThreadPool( threads, new TokenizerThreadFactory() );
  }

  private static class TokenizerThreadFactory implements ThreadFactory
  {
    private final AtomicInteger _threadNumber;

    private TokenizerThreadFactory()
    {
      _threadNumber = new AtomicInteger( 1 );
    }

    @Override
    public Thread newThread( Runnable workRunner )
    {
      Thread thread = new Thread( Thread.currentThread().getThreadGroup(), workRunner,
        "Gosu Tokenizer " + "(thread: " + _threadNumber.getAndIncrement(), 0 );
      if( !thread.isDaemon() )
      {
        thread.setDaemon( true );
      }
      if( thread.getPriority() != Thread.NORM_PRIORITY )
      {
        thread.setPriority( Thread.NORM_PRIORITY );
      }
      return thread;
    }
  }

  private List<String> getSourceFiles( CommandLineOptions options )
  {
    List<String> sourceFiles = options.getSourceFiles();
    if( !sourceFiles.isEmpty() )
    {
      return sourceFiles;
    }

    String sourcepath = options.getSourcepath();
    if( sourcepath.isEmpty() )
    {
      return Collections.emptyList();
    }

    sourceFiles = new ArrayList<>();
    for( StringTokenizer tok = new StringTokenizer( File.pathSeparator ); tok.hasMoreTokens(); )
    {
      String path = tok.nextToken();
      Path sourcePath = PathUtil.create( path );
      addToSources( sourcePath, sourceFiles );
    }

    return sourceFiles;
  }

  private void addToSources( Path sourcePath, List<String> sourceFiles )
  {
    if( !PathUtil.exists( sourcePath ) )
    {
      return;
    }

    if( Files.isDirectory( sourcePath ) )
    {
      for( Path child : PathUtil.listFiles( sourcePath ) )
      {
        addToSources( child, sourceFiles );
      }
    }
    else
    {
      String absolutePathName = PathUtil.getAbsolutePathName( sourcePath );
      if( isSourceFile( absolutePathName ) )
      {
        sourceFiles.add( absolutePathName );
      }
    }
  }

  private boolean isSourceFile( String absolutePathName )
  {
    return Arrays.stream( SOURCE_EXTS ).anyMatch( e -> absolutePathName.toLowerCase().endsWith( e ) );
  }

  private boolean compileGosuSources( CommandLineOptions options, ICompilerDriver driver, List<String> gosuFiles )
  {
    if( ASYNC_TOKENIZATION )
    {
      for( String fileName : gosuFiles )
      {
        File file = new File( fileName );
        IType type = getType( file );
        if( type == null )
        {
          driver.sendCompileIssue( _compilingSourceFile, ERROR, 0, 0, 0, "Cannot find type in the Gosu Type System." );
          shutdownTaskQueue( driver );
          return false;
        }

        if( type instanceof IGosuClass )
        {
          // preload to avoid deadlock on the lazyvar, it's the only place during warmUp() that acquires the type sys lock
          TypeSystem.getCompiledGosuClassSymbolTable();

          _taskQueue.submit( () -> ReflectUtil.method( type, "warmUp" ).invoke() );
        }
      }
    }

    try
    {
      boolean thresholdExceeded = false;
      for( String fileName : gosuFiles )
      {
        File file = new File( fileName );

        if( options.isVerbose() )
        {
          System.out.println( "gosuc: about to compile file: " + file );
        }

        compile( file, driver );

        if( driver.getErrors().size() > options.getMaxErrs() )
        {
          System.out.printf( "\nError threshold of %d exceeded; aborting compilation.", options.getMaxErrs() );
          thresholdExceeded = true;
          break;
        }
        if( !options.isNoWarn() && driver.getWarnings().size() > options.getMaxWarns() )
        {
          System.out.printf( "\nWarning threshold of %d exceeded; aborting compilation.", options.getMaxWarns() );
          thresholdExceeded = true;
          break;
        }
      }
      return thresholdExceeded;
    }
    finally
    {
      if( ASYNC_TOKENIZATION )
      {
        shutdownTaskQueue( driver );
      }
    }
  }

  private void shutdownTaskQueue( ICompilerDriver driver )
  {
    _taskQueue.shutdownNow();
    boolean terminated = false;
    try
    {
      terminated = _taskQueue.awaitTermination( 60, TimeUnit.SECONDS );
    }
    catch( InterruptedException e )
    {
      driver.sendCompileIssue( _compilingSourceFile, ERROR, 0, 0, 0, e.getMessage() );
    }
    if( !terminated )
    {
      driver.sendCompileIssue( _compilingSourceFile, ERROR, 0, 0, 0, "Failed to properly terminate tokenization queue." );
    }
  }

  private boolean compileJavaSources( CommandLineOptions options, ICompilerDriver driver, List<String> javaFiles )
  {
    IJavaParser javaParser = GosuParserFactory.getInterface( IJavaParser.class );
    DiagnosticCollector<JavaFileObject> errorHandler = new DiagnosticCollector<>();
    List<JavaFileObject> sourceFiles = javaFiles.stream().map( SourceJavaFileObject::new ).collect( Collectors.toList() );
    Collection<InMemoryClassJavaFileObject> files = javaParser.compile( sourceFiles, makeJavacOptions( options ), errorHandler );
    errorHandler.getDiagnostics().forEach( driver::sendCompileIssue );
    createJavaOutputFiles( files, driver );
    if( driver.getErrors().size() > options.getMaxErrs() )
    {
      System.out.printf( "\nError threshold of %d exceeded; aborting compilation.", options.getMaxErrs() );
      return true;
    }
    if( !options.isNoWarn() && driver.getWarnings().size() > options.getMaxWarns() )
    {
      System.out.printf( "\nWarning threshold of %d exceeded; aborting compilation.", options.getMaxWarns() );
      return true;
    }
    return false;
  }

  private List<String> makeJavacOptions( CommandLineOptions options )
  {
    ArrayList<String> javacOpts = new ArrayList<>();
    javacOpts.add( "-g" );
    javacOpts.add( "-source" );
    javacOpts.add( "8" );
    javacOpts.add( "-proc:none" );
    javacOpts.add( "-Xlint:unchecked" );
    javacOpts.add( "-parameters" );
    if( options.isVerbose() )
    {
      javacOpts.add( "-verbose" );
    }
    if( options.isNoWarn() )
    {
      javacOpts.add( "-nowarn" );
    }
    return javacOpts;
  }

  @Override
  public boolean compile( File sourceFile, ICompilerDriver driver )
  {
    _compilingSourceFile = sourceFile;

    IType type = getType( _compilingSourceFile );
    if( type == null )
    {
      driver.sendCompileIssue( _compilingSourceFile, ERROR, 0, 0, 0, "Cannot find type in the Gosu Type System." );
      return false;
    }

    if( isCompilable( type ) )
    {
      try
      {
        if( type.isValid() )
        {
          createGosuOutputFiles( (IGosuClass)type, driver );
        }
      }
      catch( CompilerDriverException ex )
      {
        driver.sendCompileIssue( _compilingSourceFile, ERROR, 0, 0, 0, ex.getMessage() );
        return false;
      }
      // output warnings and errors - whether the type was valid or not
      IParsedElement classElement = ((IGosuClass)type).getClassStatement();
      IClassFileStatement classFileStatement = ((IClassStatement)classElement).getClassFileStatement();
      classElement = classFileStatement == null ? classElement : classFileStatement;
      ExecutionMode mode = CommonServices.getPlatformHelper().getExecutionMode();
      for( IParseIssue issue : classElement.getParseIssues() )
      {
        int category = issue instanceof ParseWarning ? WARNING : ERROR;
        String message = mode == ExecutionMode.IDE ? issue.getUIMessage() : issue.getConsoleMessage();
        driver.sendCompileIssue( _compilingSourceFile, category, issue.getTokenStart(), issue.getLine(), issue.getColumn(), message );
      }
    }

    return false;
  }

  private IType getType( File file )
  {
    IFile ifile = FileFactory.instance().getIFile( file );
    IModule module = TypeSystem.getGlobalModule();
    String[] typesForFile = TypeSystem.getTypesForFile( module, ifile );
    if( typesForFile.length != 0 )
    {
      return TypeSystem.getByFullNameIfValid( typesForFile[0], module );
    }
    return null;
  }

  private boolean isCompilable( IType type )
  {
    IType doNotVerifyAnnotation = TypeSystem.getByFullNameIfValid( "gw.testharness.DoNotVerifyResource" );
    return type instanceof IGosuClass && !type.getTypeInfo().hasAnnotation( doNotVerifyAnnotation );
  }

  private void createGosuOutputFiles( IGosuClass gsClass, ICompilerDriver driver )
  {
    IDirectory moduleOutputDirectory = TypeSystem.getGlobalModule().getOutputPath();
    if( moduleOutputDirectory == null )
    {
      throw new RuntimeException( "Can't make class file, no output path defined." );
    }

    final String outRelativePath = gsClass.getName().replace( '.', File.separatorChar ) + ".class";
    File child = new File( moduleOutputDirectory.getPath().getFileSystemPathString() );
    mkdirs( child );
    try
    {
      for( StringTokenizer tokenizer = new StringTokenizer( outRelativePath, File.separator + "/" ); tokenizer.hasMoreTokens(); )
      {
        String token = tokenizer.nextToken();
        child = new File( child, token );
        if( !child.exists() )
        {
          if( token.endsWith( ".class" ) )
          {
            createNewFile( child );
          }
          else
          {
            mkDir( child );
          }
        }
      }
      populateGosuClassFile( child, gsClass, driver );
      maybeCopySourceFile( child.getParentFile(), gsClass, _compilingSourceFile, driver );
    }
    catch( Throwable e )
    {
      driver.sendCompileIssue( _compilingSourceFile, ERROR, 0, 0, 0, combine( "Cannot create .class files.", getStackTrace( e ) ) );
    }
  }

  private void createJavaOutputFiles( Collection<InMemoryClassJavaFileObject> compiledJavaFiles, ICompilerDriver driver )
  {
    IDirectory moduleOutputDirectory = TypeSystem.getGlobalModule().getOutputPath();
    if( moduleOutputDirectory == null )
    {
      throw new RuntimeException( "Can't make class file, no output path defined." );
    }

    compiledJavaFiles = compiledJavaFiles.stream().filter( e -> TypeSystem.getByFullNameIfValid( e.getClassName().replace( '$', '.' ) ) instanceof IJavaType ).collect( Collectors.toList() );

    for( InMemoryClassJavaFileObject compiledJavaFile: compiledJavaFiles )
    {
      final String outRelativePath = compiledJavaFile.getClassName().replace( '.', File.separatorChar ) + ".class";
      File child = new File( moduleOutputDirectory.getPath().getFileSystemPathString() );
      mkdirs( child );
      try
      {
        for( StringTokenizer tokenizer = new StringTokenizer( outRelativePath, File.separator + "/" ); tokenizer.hasMoreTokens(); )
        {
          String token = tokenizer.nextToken();
          child = new File( child, token );
          if( !child.exists() )
          {
            if( token.endsWith( ".class" ) )
            {
              createNewFile( child );
            }
            else
            {
              mkDir( child );
            }
          }
        }
        populateJavaClassFile( child, compiledJavaFile.getBytes(), driver );
      }
      catch( Throwable e )
      {
        driver.sendCompileIssue( _compilingSourceFile, ERROR, 0, 0, 0, combine( "Cannot create .class files.", getStackTrace( e ) ) );
      }
    }
  }

  public static String getStackTrace( Throwable e )
  {
    StringWriter stringWriter = new StringWriter();
    e.printStackTrace( new PrintWriter( stringWriter ) );
    return stringWriter.toString();
  }

  private String combine( String message1, String message2 )
  {
    if( message1 == null )
    {
      message1 = "";
    }
    else
    {
      message1 = message1 + "\n";
    }
    return message1 + message2;
  }

  private boolean mkDir( File file )
  {
    return file.mkdir();
  }

  private boolean mkdirs( File file )
  {
    return file.mkdirs();
  }

  private boolean createNewFile( File file ) throws IOException
  {
    return file.createNewFile();
  }

  private void maybeCopySourceFile( File parent, IGosuClass gsClass, File sourceFile, ICompilerDriver driver )
  {
    ISourceFileHandle sfh = gsClass.getSourceFileHandle();
    IFile srcFile = sfh.getFile();
    if( srcFile != null )
    {
      File file = new File( srcFile.getPath().getFileSystemPathString() );
      if( file.isFile() )
      {
        try
        {
          File destFile = new File( parent, file.getName() );
          copyFile( file, destFile );
          driver.registerOutput( _compilingSourceFile, destFile );
        }
        catch( IOException e )
        {
          e.printStackTrace();
          driver.sendCompileIssue( sourceFile, ERROR, 0, 0, 0, "Cannot copy source file to output folder." );
        }
      }
    }
  }

  public void copyFile( File sourceFile, File destFile ) throws IOException
  {
    if( sourceFile.isDirectory() )
    {
      mkdirs( destFile );
      return;
    }

    if( !destFile.exists() )
    {
      mkdirs( destFile.getParentFile() );
      createNewFile( destFile );
    }

    try( FileChannel source = new FileInputStream( sourceFile ).getChannel();
         FileChannel destination = new FileOutputStream( destFile ).getChannel() )
    {
      destination.transferFrom( source, 0, source.size() );
    }
  }

  private void populateGosuClassFile( File outputFile, IGosuClass gosuClass, ICompilerDriver driver ) throws IOException
  {
    final byte[] bytes = TypeSystem.getGosuClassLoader().getBytes( gosuClass );
    try( OutputStream out = new FileOutputStream( outputFile ) )
    {
      out.write( bytes );
      driver.registerOutput( _compilingSourceFile, outputFile );
    }
    for( IGosuClass innerClass : gosuClass.getInnerClasses() )
    {
      final String innerClassName = String.format( "%s$%s.class", outputFile.getName().substring( 0, outputFile.getName().lastIndexOf( '.' ) ), innerClass.getRelativeName() );
      File innerClassFile = new File( outputFile.getParent(), innerClassName );
      if( innerClassFile.isFile() )
      {
        createNewFile( innerClassFile );
      }
      populateGosuClassFile( innerClassFile, innerClass, driver );
    }
  }

  private void populateJavaClassFile( File outputFile, byte[] bytes, ICompilerDriver driver ) throws IOException
  {
    try( OutputStream out = new FileOutputStream( outputFile ) )
    {
      out.write( bytes );
      driver.registerOutput( _compilingSourceFile, outputFile );
    }
  }

  @Override
  public long initializeGosu( List<String> sourceFolders, List<String> classpath, List<String> backingSourcePath, String outputPath )
  {
    NecessaryEvilUtil.bypassJava9Security();

    final long start = System.currentTimeMillis();

    CommonServices.getKernel().redefineService_Privileged( IFileSystem.class, createFileSystemInstance() );
    CommonServices.getKernel().redefineService_Privileged( IMemoryMonitor.class, new CompilerMemoryMonitor() );
    CommonServices.getKernel().redefineService_Privileged( IPlatformHelper.class, new CompilerPlatformHelper() );

    if( "gw".equals( System.getProperty( "compiler.type" ) ) )
    {
      try
      {
        IEntityAccess access = (IEntityAccess)Class.forName( "gw.internal.gosu.parser.gwPlatform.GWEntityAccess" ).newInstance();
        ICoercionManager coercionManager = (ICoercionManager)Class.forName( "gw.internal.gosu.parser.gwPlatform.GWCoercionManager" ).newInstance();
        CommonServices.getKernel().redefineService_Privileged( IEntityAccess.class, access );
        CommonServices.getKernel().redefineService_Privileged( ICoercionManager.class, coercionManager );
        Registry.instance().setAllowEntityQueires( true );
      }
      catch( Exception e )
      {
        throw new RuntimeException( e );
      }
    }

    IExecutionEnvironment execEnv = TypeSystem.getExecutionEnvironment();
    _gosuInitialization = GosuInitialization.instance( execEnv );
    GosucModule gosucModule = new GosucModule( IExecutionEnvironment.DEFAULT_SINGLE_MODULE_NAME,
                                               sourceFolders,
                                               classpath,
                                               backingSourcePath,
                                               outputPath,
                                               Collections.<GosucDependency>emptyList(),
                                               Collections.<String>emptyList() );
    _gosuInitialization.initializeCompiler( gosucModule );

    return System.currentTimeMillis() - start;
  }

  private static IFileSystem createFileSystemInstance()
  {
    try
    {
      Class<?> cls = Class.forName( "gw.internal.gosu.module.fs.FileSystemImpl" );
      Constructor m = cls.getConstructor( IFileSystem.CachingMode.class );
      return (IFileSystem)m.newInstance( IFileSystem.CachingMode.FULL_CACHING );
    }
    catch( Exception e )
    {
      throw new RuntimeException( e );
    }
  }

  @Override
  public void uninitializeGosu()
  {
    TypeSystem.shutdown( TypeSystem.getExecutionEnvironment() );
    if( _gosuInitialization != null )
    {
      if( _gosuInitialization.isInitialized() )
      {
        _gosuInitialization.uninitializeCompiler();
      }
      _gosuInitialization = null;
    }
  }

  @Override
  public boolean isPathIgnored( String sourceFile )
  {
    return CommonServices.getPlatformHelper().isPathIgnored( sourceFile );
  }
}
