/*
 * This file is a shadowed version of the older javadoc codebase on which gosudoc is based; borrowed from jdk 9.
 */

package gw.gosudoc.com.sun.tools.doclets.formats.html;

import java.net.*;
import java.util.*;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;




import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.StringUtils;
import gw.gosudoc.com.sun.javadoc.PackageDoc;
import gw.gosudoc.com.sun.tools.doclets.internal.toolkit.Configuration;
import gw.gosudoc.com.sun.tools.doclets.internal.toolkit.Content;
import gw.gosudoc.com.sun.tools.doclets.internal.toolkit.WriterFactory;
import gw.gosudoc.com.sun.tools.doclets.internal.toolkit.util.*;
import gw.gosudoc.com.sun.tools.javadoc.main.JavaScriptScanner;
import gw.gosudoc.com.sun.tools.javadoc.main.RootDocImpl;
import gw.gosudoc.com.sun.tools.doclets.formats.html.markup.ContentBuilder;
import gw.gosudoc.com.sun.tools.doclets.formats.html.markup.HtmlTag;
import gw.gosudoc.com.sun.tools.doclets.formats.html.markup.HtmlVersion;

/**
 * Configure the output based on the command line options.
 * <p>
 * Also determine the length of the command line option. For example,
 * for a option "-header" there will be a string argument associated, then the
 * the length of option "-header" is two. But for option "-nohelp" no argument
 * is needed so it's length is 1.
 * </p>
 * <p>
 * Also do the error checking on the options used. For example it is illegal to
 * use "-helpfile" option when already "-nohelp" option is used.
 * </p>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field.
 * @author Atul Dambalkar.
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
@Deprecated
public class ConfigurationImpl extends Configuration
{

    /**
     * The build date.  Note: For now, we will use
     * a version number instead of a date.
     */
    public static final String BUILD_DATE = System.getProperty("java.version");

    /**
     * Argument for command line option "-header".
     */
    public String header = "";

    /**
     * Argument for command line option "-packagesheader".
     */
    public String packagesheader = "";

    /**
     * Argument for command line option "-footer".
     */
    public String footer = "";

    /**
     * Argument for command line option "-doctitle".
     */
    public String doctitle = "";

    /**
     * Argument for command line option "-windowtitle".
     */
    public String windowtitle = "";

    /**
     * Argument for command line option "-top".
     */
    public String top = "";

    /**
     * Argument for command line option "-bottom".
     */
    public String bottom = "";

    /**
     * Argument for command line option "-helpfile".
     */
    public String helpfile = "";

    /**
     * Argument for command line option "-stylesheetfile".
     */
    public String stylesheetfile = "";

    /**
     * Argument for command line option "-Xdocrootparent".
     */
    public String docrootparent = "";

    /**
     * True if command line option "-nohelp" is used. Default value is false.
     */
    public boolean nohelp = false;

    /**
     * True if command line option "-splitindex" is used. Default value is
     * false.
     */
    public boolean splitindex = false;

    /**
     * False if command line option "-noindex" is used. Default value is true.
     */
    public boolean createindex = true;

    /**
     * True if command line option "-use" is used. Default value is false.
     */
    public boolean classuse = false;

    /**
     * False if command line option "-notree" is used. Default value is true.
     */
    public boolean createtree = true;

    /**
     * True if command line option "-nodeprecated" is used. Default value is
     * false.
     */
    public boolean nodeprecatedlist = false;

    /**
     * True if command line option "-nonavbar" is used. Default value is false.
     */
    public boolean nonavbar = false;

    /**
     * True if command line option "-nooverview" is used. Default value is
     * false
     */
    private boolean nooverview = false;

    /**
     * True if command line option "-overview" is used. Default value is false.
     */
    public boolean overview = false;

    /**
     * This is true if option "-overview" is used or option "-overview" is not
     * used and number of packages is more than one.
     */
    public boolean createoverview = false;

    /**
     * This is the HTML version of the generated pages. HTML 4.01 is the default output version.
     */
    public HtmlVersion htmlVersion = HtmlVersion.HTML4;

    /**
     * Collected set of doclint options
     */
    public Set<String> doclintOpts = new LinkedHashSet<>();

    /**
     * Whether or not to check for JavaScript in doc comments.
     */
    private boolean allowScriptInComments;

    /**
     * Unique Resource Handler for this package.
     */
    public final MessageRetriever standardmessage;

    /**
     * First file to appear in the right-hand frame in the generated
     * documentation.
     */
    public DocPath topFile = DocPath.empty;

    /**
     * The classdoc for the class file getting generated.
     */
    public gw.gosudoc.com.sun.javadoc.ClassDoc currentcd = null;  // Set this classdoc in the ClassWriter.

    protected List<SearchIndexItem> memberSearchIndex = new ArrayList<>();

    protected List<SearchIndexItem> packageSearchIndex = new ArrayList<>();

    protected List<SearchIndexItem> tagSearchIndex = new ArrayList<>();

    protected List<SearchIndexItem> typeSearchIndex = new ArrayList<>();

    protected Map<Character,List<SearchIndexItem>> tagSearchIndexMap = new HashMap<>();

    protected Set<Character> tagSearchIndexKeys;

    /**
     * Constructor. Initializes resource for the
     * {@link MessageRetriever MessageRetriever}.
     */
    public ConfigurationImpl() {
        standardmessage = new MessageRetriever(this,
            "gw.gosudoc.com.sun.tools.doclets.formats.html.resources.standard");
    }

    private final String versionRBName = "gw.gosudoc.com.sun.tools.javadoc.resources.version";
    private ResourceBundle versionRB;

    /**
     * Return the build date for the doclet.
     */
    @Override
    public String getDocletSpecificBuildDate() {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
            } catch (MissingResourceException e) {
                return BUILD_DATE;
            }
        }

        try {
            return versionRB.getString("release");
        } catch (MissingResourceException e) {
            return BUILD_DATE;
        }
    }

    /**
     * Depending upon the command line options provided by the user, set
     * configure the output generation environment.
     *
     * @param options The array of option names and values.
     */
    @Override
    public void setSpecificDocletOptions(String[][] options) {
        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = StringUtils.toLowerCase(os[0]);
            if (opt.equals("-footer")) {
                footer = os[1];
            } else if (opt.equals("-header")) {
                header = os[1];
            } else if (opt.equals("-packagesheader")) {
                packagesheader = os[1];
            } else if (opt.equals("-doctitle")) {
                doctitle = os[1];
            } else if (opt.equals("-windowtitle")) {
                windowtitle = os[1].replaceAll("\\<.*?>", "");
            } else if (opt.equals("-top")) {
                top = os[1];
            } else if (opt.equals("-bottom")) {
                bottom = os[1];
            } else if (opt.equals("-helpfile")) {
                helpfile = os[1];
            } else if (opt.equals("-stylesheetfile")) {
                stylesheetfile = os[1];
            } else if (opt.equals("-charset")) {
                charset = os[1];
            } else if (opt.equals("-xdocrootparent")) {
                docrootparent = os[1];
            } else if (opt.equals("-nohelp")) {
                nohelp = true;
            } else if (opt.equals("-splitindex")) {
                splitindex = true;
            } else if (opt.equals("-noindex")) {
                createindex = false;
            } else if (opt.equals("-use")) {
                classuse = true;
            } else if (opt.equals("-notree")) {
                createtree = false;
            } else if (opt.equals("-nodeprecatedlist")) {
                nodeprecatedlist = true;
            } else if (opt.equals("-nonavbar")) {
                nonavbar = true;
            } else if (opt.equals("-nooverview")) {
                nooverview = true;
            } else if (opt.equals("-overview")) {
                overview = true;
            } else if (opt.equals("-html4")) {
                htmlVersion = HtmlVersion.HTML4;
            } else if (opt.equals("-html5")) {
                htmlVersion = HtmlVersion.HTML5;
            } else if (opt.equals("-xdoclint")) {
                doclintOpts.add(DocLint.XMSGS_OPTION);
            } else if (opt.startsWith("-xdoclint:")) {
                doclintOpts.add(DocLint.XMSGS_CUSTOM_PREFIX + opt.substring(opt.indexOf(":") + 1));
            } else if (opt.startsWith("-xdoclint/package:")) {
                doclintOpts.add(DocLint.XCHECK_PACKAGE + opt.substring(opt.indexOf(":") + 1));
            } else if (opt.equals("--allow-script-in-comments")) {
                allowScriptInComments = true;
            }
        }

        if (root.specifiedClasses().length > 0) {
            Map<String, gw.gosudoc.com.sun.javadoc.PackageDoc> map = new HashMap<>();
            gw.gosudoc.com.sun.javadoc.PackageDoc pd;
            gw.gosudoc.com.sun.javadoc.ClassDoc[] classes = root.classes();
            for ( gw.gosudoc.com.sun.javadoc.ClassDoc aClass : classes) {
                pd = aClass.containingPackage();
                if (!map.containsKey(pd.name())) {
                    map.put(pd.name(), pd);
                }
            }
        }

        setCreateOverview();
        setTopFile(root);

        if (root instanceof RootDocImpl) {
            ((RootDocImpl) root).initDocLint(doclintOpts, tagletManager.getCustomTagNames(),
                    StringUtils.toLowerCase(htmlVersion.name()));
            JavaScriptScanner jss = ((RootDocImpl) root).initJavaScriptScanner(isAllowScriptInComments());
            if (jss != null) {
                // In a more object-oriented world, this would be done by methods on the Option objects.
                // Note that -windowtitle silently removes any and all HTML elements, and so does not need
                // to be handled here.
                checkJavaScript(jss, "-header", header);
                checkJavaScript(jss, "-footer", footer);
                checkJavaScript(jss, "-top", top);
                checkJavaScript(jss, "-bottom", bottom);
                checkJavaScript(jss, "-doctitle", doctitle);
                checkJavaScript(jss, "-packagesheader", packagesheader);
            }
        }
    }

    private void checkJavaScript(JavaScriptScanner jss, final String opt, String value) {
        jss.parse(value, new JavaScriptScanner.Reporter() {
            public void report() {
                root.printError(getText("doclet.JavaScript_in_option", opt));
                throw new FatalError();
            }
        });
    }

    /**
     * Returns the "length" of a given option. If an option takes no
     * arguments, its length is one. If it takes one argument, it's
     * length is two, and so on. This method is called by JavaDoc to
     * parse the options it does not recognize. It then calls
     * {@link #validOptions(String[][], gw.gosudoc.com.sun.javadoc.DocErrorReporter)} to
     * validate them.
     * <b>Note:</b><br>
     * The options arrive as case-sensitive strings. For options that
     * are not case-sensitive, use toLowerCase() on the option string
     * before comparing it.
     *
     * @return number of arguments + 1 for a option. Zero return means
     * option not known.  Negative value means error occurred.
     */
    public int optionLength(String option) {
        int result = -1;
        if ((result = super.optionLength(option)) > 0) {
            return result;
        }
        // otherwise look for the options we have added
        option = StringUtils.toLowerCase(option);
        if (option.equals("-nodeprecatedlist") ||
            option.equals("-noindex") ||
            option.equals("-notree") ||
            option.equals("-nohelp") ||
            option.equals("-splitindex") ||
            option.equals("-serialwarn") ||
            option.equals("-use") ||
            option.equals("-nonavbar") ||
            option.equals("-nooverview") ||
            option.equals("-html4") ||
            option.equals("-html5") ||
            option.equals("-xdoclint") ||
            option.startsWith("-xdoclint:") ||
            option.startsWith("-xdoclint/package:") ||
            option.startsWith("--allow-script-in-comments")) {
            return 1;
        } else if (option.equals("-help")) {
            // Uugh: first, this should not be hidden inside optionLength,
            // and second, we should not be writing directly to stdout.
            // But we have no access to a DocErrorReporter, which would
            // allow use of reporter.printNotice
            System.out.println(getText("doclet.usage"));
            return 1;
        } else if (option.equals("-x")) {
            // Uugh: first, this should not be hidden inside optionLength,
            // and second, we should not be writing directly to stdout.
            // But we have no access to a DocErrorReporter, which would
            // allow use of reporter.printNotice
            System.out.println(getText("doclet.X.usage"));
            return 1;
        } else if (option.equals("-footer") ||
                   option.equals("-header") ||
                   option.equals("-packagesheader") ||
                   option.equals("-doctitle") ||
                   option.equals("-windowtitle") ||
                   option.equals("-top") ||
                   option.equals("-bottom") ||
                   option.equals("-helpfile") ||
                   option.equals("-stylesheetfile") ||
                   option.equals("-charset") ||
                   option.equals("-overview") ||
                   option.equals("-xdocrootparent")) {
            return 2;
        } else {
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validOptions(String options[][],
            gw.gosudoc.com.sun.javadoc.DocErrorReporter reporter) {
        boolean helpfile = false;
        boolean nohelp = false;
        boolean overview = false;
        boolean nooverview = false;
        boolean splitindex = false;
        boolean noindex = false;
        // check shared options
        if (!generalValidOptions(options, reporter)) {
            return false;
        }
        // otherwise look at our options
        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = StringUtils.toLowerCase(os[0]);
            if (opt.equals("-helpfile")) {
                if (nohelp == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-helpfile", "-nohelp"));
                    return false;
                }
                if (helpfile == true) {
                    reporter.printError(getText("doclet.Option_reuse",
                        "-helpfile"));
                    return false;
                }
                DocFile help = DocFile.createFileForInput(this, os[1]);
                if (!help.exists()) {
                    reporter.printError(getText("doclet.File_not_found", os[1]));
                    return false;
                }
                helpfile = true;
            } else  if (opt.equals("-nohelp")) {
                if (helpfile == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-nohelp", "-helpfile"));
                    return false;
                }
                nohelp = true;
            } else if (opt.equals("-xdocrootparent")) {
                try {
                    new URL(os[1]);
                } catch (MalformedURLException e) {
                    reporter.printError(getText("doclet.MalformedURL", os[1]));
                    return false;
                }
            } else if (opt.equals("-overview")) {
                if (nooverview == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-overview", "-nooverview"));
                    return false;
                }
                if (overview == true) {
                    reporter.printError(getText("doclet.Option_reuse",
                        "-overview"));
                    return false;
                }
                overview = true;
            } else  if (opt.equals("-nooverview")) {
                if (overview == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-nooverview", "-overview"));
                    return false;
                }
                nooverview = true;
            } else if (opt.equals("-splitindex")) {
                if (noindex == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-splitindex", "-noindex"));
                    return false;
                }
                splitindex = true;
            } else if (opt.equals("-noindex")) {
                if (splitindex == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-noindex", "-splitindex"));
                    return false;
                }
                noindex = true;
            } else if (opt.startsWith("-xdoclint:")) {
                if (opt.contains("/")) {
                    reporter.printError(getText("doclet.Option_doclint_no_qualifiers"));
                    return false;
                }
//                if (!DocLint.isValidOption(
//                        opt.replace("-xdoclint:", DocLint.XMSGS_CUSTOM_PREFIX))) {
//                    reporter.printError(getText("doclet.Option_doclint_invalid_arg"));
//                    return false;
//                }
            } else if (opt.startsWith("-xdoclint/package:")) {
//                if (!DocLint.isValidOption(
//                        opt.replace("-xdoclint/package:", DocLint.XCHECK_PACKAGE))) {
//                    reporter.printError(getText("doclet.Option_doclint_package_invalid_arg"));
//                    return false;
//                }
            }
        }
        return true;
    }

    /**
     * Return true if the generated output is HTML5.
     */
    public boolean isOutputHtml5() {
        return htmlVersion == HtmlVersion.HTML5;
    }

    /**
     * Return true if the tag is allowed for this specific version of HTML.
     */
    public boolean allowTag( HtmlTag htmlTag) {
        return htmlTag.allowTag(this.htmlVersion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageRetriever getDocletSpecificMsg() {
        return standardmessage;
    }

    /**
     * Decide the page which will appear first in the right-hand frame. It will
     * be "overview-summary.html" if "-overview" option is used or no
     * "-overview" but the number of packages is more than one. It will be
     * "package-summary.html" of the respective package if there is only one
     * package to document. It will be a class page(first in the sorted order),
     * if only classes are provided on the command line.
     *
     * @param root Root of the program structure.
     */
    protected void setTopFile( gw.gosudoc.com.sun.javadoc.RootDoc root) {
        if (!checkForDeprecation(root)) {
            return;
        }
        if (createoverview) {
            topFile = DocPaths.OVERVIEW_SUMMARY;
        } else {
            if (packages.size() == 1 && packages.first().name().equals("")) {
                if (root.classes().length > 0) {
                    gw.gosudoc.com.sun.javadoc.ClassDoc[] classarr = root.classes();
                    Arrays.sort(classarr);
                    gw.gosudoc.com.sun.javadoc.ClassDoc cd = getValidClass(classarr);
                    topFile = DocPath.forClass(cd);
                }
            } else if (!packages.isEmpty()) {
                topFile = DocPath.forPackage(packages.first()).resolve(DocPaths.PACKAGE_SUMMARY);
            }
        }
    }

    protected gw.gosudoc.com.sun.javadoc.ClassDoc getValidClass( gw.gosudoc.com.sun.javadoc.ClassDoc[] classarr) {
        if (!nodeprecated) {
            return classarr[0];
        }
        for ( gw.gosudoc.com.sun.javadoc.ClassDoc cd : classarr) {
            if (cd.tags("deprecated").length == 0) {
                return cd;
            }
        }
        return null;
    }

    protected boolean checkForDeprecation( gw.gosudoc.com.sun.javadoc.RootDoc root) {
        for ( gw.gosudoc.com.sun.javadoc.ClassDoc cd : root.classes()) {
            if (isGeneratedDoc(cd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate "overview.html" page if option "-overview" is used or number of
     * packages is more than one. Sets {@link #createoverview} field to true.
     */
    protected void setCreateOverview() {
        if ((overview || packages.size() > 1) && !nooverview) {
            createoverview = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WriterFactory getWriterFactory() {
        return new WriterFactoryImpl(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Comparator<gw.gosudoc.com.sun.javadoc.ProgramElementDoc> getMemberComparator() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
        if (root instanceof RootDocImpl)
            return ((RootDocImpl)root).getLocale();
        else
            return Locale.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaFileManager getFileManager() {
        if (fileManager == null) {
            if (root instanceof RootDocImpl)
                fileManager = ((RootDocImpl) root).getFileManager();
            else
                fileManager = new JavacFileManager(new Context(), false, null);
        }
        return fileManager;
    }

    private JavaFileManager fileManager;

    @Override
    public boolean showMessage( gw.gosudoc.com.sun.javadoc.SourcePosition pos, String key) {
        if (root instanceof RootDocImpl) {
            return pos == null || ((RootDocImpl) root).showTagMessages();
        }
        return true;
    }

    @Override
    public Content newContent() {
        return new ContentBuilder();
    }

    @Override
    public Location getLocationForPackage( PackageDoc pd) {
        JavaFileManager fm = getFileManager();
        return StandardLocation.SOURCE_PATH;
    }

    protected void buildSearchTagIndex() {
        for (SearchIndexItem sii : tagSearchIndex) {
            String tagLabel = sii.getLabel();
            char ch = (tagLabel.length() == 0)
                    ? '*'
                    : Character.toUpperCase(tagLabel.charAt(0));
            Character unicode = ch;
            List<SearchIndexItem> list = tagSearchIndexMap.get(unicode);
            if (list == null) {
                list = new ArrayList<>();
                tagSearchIndexMap.put(unicode, list);
            }
            list.add(sii);
        }
        tagSearchIndexKeys = tagSearchIndexMap.keySet();
    }

    /**
     * Returns whether or not to allow JavaScript in comments.
     * Default is off; can be set true from a command line option.
     * @return the allowScriptInComments
     */
    public boolean isAllowScriptInComments() {
        return allowScriptInComments;
    }
}