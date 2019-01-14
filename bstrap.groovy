#!/bin/env groovy

// source:
// https://picocli.info/picocli-2.0-groovy-scripts-on-steroids.html

import groovy.transform.SourceURI

import javax.rmi.CORBA.Util
import java.nio.file.Path
import java.nio.file.Paths
import groovy.xml.QName
import groovy.xml.XmlUtil
import groovy.transform.ToString


// -------------------------------------------------------------------------------------------------------------------
// imports

@Grab('info.picocli:picocli:3.8.0')
@Command(name = "bstrap", description = "bootstraps a new project.\n For completion run \nbstrap.groovy -g && . /home/mark/bin/bstrap_completion", subcommands =[
        Add.class,
        MvnGenerate.class
])
@picocli.groovy.PicocliScript
import groovy.transform.Field
import java.security.MessageDigest
import static picocli.CommandLine.*

//@Grab('org.jboss.forge.roaster:roaster-api:2.11.1.Final')
//@Grab('org.jboss.forge.roaster:roaster-jdt:2.11.1.Final')
@Grab('org.jboss.forge.roaster:roaster-api:2.20.4.Final')
@Grab('org.jboss.forge.roaster:roaster-jdt:2.20.4.Final')
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;


// -------------------------------------------------------------------------------------------------------------------
// enums

enum MvnArchetype {

//    alias scaff-mvn-archetype-generate-simplejar='mvn archetype:generate
    simpleJar("org.apache.maven.archetypes", "maven-archetype-quickstart", "RELEASE"),
    multiModule("org.codehaus.mojo.archetypes", "pom-root", "RELEASE"),

    final String groupId
    final String artifactId
    final String version

    MvnArchetype(String groupId, String artifactId, String version) {
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    String toReadableString() {
        "$groupId:$artifactId:$version"
    }
}

enum Dependency {
    javacord("org.javacord", "javacord", "3.0.1", "pom", "compile"),
    lombok("org.projectlombok", "lombok", "1.18.4", "jar", "provided")

    final String groupId
    final String artifactId
    final String version
    final String type
    final String scope

    Dependency(String groupId, String artifactId, String version, String type, String scope) {
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
        this.type = type
        this.scope = scope
    }

    String toReadableString() {
        "$groupId:$artifactId:$version:$type"
    }

    String toMavenString() {
        String rawDependency = """
        <!-- https://mvnrepository.com/artifact/$groupId/$artifactId -->
        <dependency>
            <groupId>${groupId}</groupId>
            <artifactId>${artifactId}</artifactId>
            <version>${version}</version>
            <type>${type}</type>
            <scope>${scope}</scope>
        </dependency>
"""

        rawDependency
                .replaceAll(" *<type>jar.*", "")
                .replaceAll(" *<scope>compile.*", "")
                .replaceAll("\n+", "\n")
    }
}

enum PredefinedArchetype {
    multiModule("org.codehaus.mojo.archetypes", "pom-root", "1.1"),
    simpleJar("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.4")
    ;

    private final String groupId
    private final String artifactId
    private final String version

    PredefinedArchetype(String groupId, String artifactId, String version) {
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }
}


// -------------------------------------------------------------------------------------------------------------------
// Utility classes

class Utils {

    static def String user() { System.getenv("USER") }

    static void writeToFileIfNotExists(File directory, String attributes, String fileName) {
        def attributesFile = new File(directory.toString() + "/" + fileName)
        if (attributesFile.exists()) {
            println "File $attributesFile exists already, skipping"
        } else {
            attributesFile.write attributes
            println "File written to $attributesFile"
        }
    }

    static void executeCLI(String cli) {
        def proc = cli.execute()
        proc.waitForProcessOutput(System.out, System.err)
//        def b = new StringBuffer()
//        proc.consumeProcessErrorStream(b)

//        println proc.text
//        println b.toString()

        println ""
    }
}

// -------------------------------------------------------------------------------------------------------------------
// Services

class Global {
    URI sourceURI

    Global(URI sourceURI) {
        Objects.requireNonNull(sourceURI)
        this.sourceURI = sourceURI
    }

    String root() {
        Path scriptLocation = Paths.get(sourceURI)
        scriptLocation.parent.parent.parent.toString()
    }

    String bstrapCompletionPath() { Paths.get(sourceURI).parent.toString() + "/bstrap_completion" }
}

class EditorConfigService {
    def addEditorConfig(File file) {
        String editorConfig = """
# EditorConfig helps developers define and maintain consistent
# coding styles between different editors and IDEs
# editorconfig.org

root = true

[*]

# Change these settings to your own preference
indent_style = space
indent_size = 4

# We recommend you to keep these unchanged
end_of_line = lf
charset = utf-8
trim_trailing_whitespace = true
insert_final_newline = true

[*.md]
trim_trailing_whitespace = false

[{package,bower}.json]
indent_style = space
indent_size = 2
"""
        Utils.writeToFileIfNotExists(file, editorConfig, ".editorconfig")
    }
}

class GitService {

    def addIgnore(File file) {
        String gitignore = """
# Created by https://www.gitignore.io

### Linux ###
*~

# KDE directory preferences
.directory


### Intellij ###
# Covers JetBrains IDEs: IntelliJ, RubyMine, PhpStorm, AppCode, PyCharm

*.iml

## Directory-based project format:
.idea/
# if you remove the above rule, at least ignore the following:

# User-specific stuff:
# .idea/workspace.xml
# .idea/tasks.xml
# .idea/dictionaries

# Sensitive or high-churn files:
# .idea/dataSources.ids
# .idea/dataSources.xml
# .idea/sqlDataSources.xml
# .idea/dynamic.xml
# .idea/uiDesigner.xml

# Gradle:
# .idea/gradle.xml
# .idea/libraries

# Mongo Explorer plugin:
# .idea/mongoSettings.xml

## File-based project format:
*.ipr
*.iws

## Plugin-specific files:

# IntelliJ
out/

# mpeltonen/sbt-idea plugin
.idea_modules/

# JIRA plugin
atlassian-ide-plugin.xml

# Crashlytics plugin (for Android Studio and IntelliJ)
com_crashlytics_export_strings.xml


### Java ###
*.class

# Mobile Tools for Java (J2ME)
.mtj.tmp/

# Package Files #
*.jar
*.war
*.ear

# virtual machine crash logs, see http://www.java.com/en/download/help/error_hotspot.xml
hs_err_pid*

target/
"""
        Utils.writeToFileIfNotExists(file, gitignore, ".gitignore")
    }

    def addAttributes(File file) {
        String attributes = """
# found here:
# https://github.com/Danimoth/gitattributes/blob/master/Java.gitattributes
# and extended a bit

# Handle line endings automatically for files detected as text
# and leave all files detected as binary untouched.
* text=auto

#
# The above will handle all files NOT found below
#

# GENERIC patterns
*.gitattributes text
.gitignore      text
*.sh            eol=lf
*.bat           eol=crlf
*.log           text

# JAVA patterns
#
# These files are text and should be normalized (Convert crlf => lf), lf in the repository
*.md            text
*.adoc          text
*.textile       text
*.mustache      text
*.csv           text
*.tab           text
*.tsv           text
*.css           text
*.df            text
*.htm           text
*.html          text
*.java          text
*.js            text
*.json          text
*.jsp           text
*.jspf          text
*.properties    text
*.sh            text
*.sql           text
*.svg           text
*.tld           text
*.txt           text
*.xml           text
*.sql           text

# These files are binary and should be left untouched
# (binary is a macro for -text -diff)
*.class         binary
*.dll           binary
*.ear           binary
*.gif           binary
*.ico           binary
*.jar           binary
*.jpg           binary
*.jpeg          binary
*.png           binary
*.so            binary
*.war           binary


# PYTHON patterns
#
*.py            text
*.pyc           binary
*.pro           text
*.rst           text
*.ts            text
*.ui            text
*.qm            binary
"""

        Utils.writeToFileIfNotExists(file, attributes, ".gitattributes")
    }
}

@ToString
class ArchetypeModuleOption {

    private static final String DEFAULT_VALUE = "com.company:project:1.0-SNAPSHOT:com.company.project"
    private static final String DEFAULT_VERSION = "1.0-SNAPSHOT"

    String groupId
    String artifactId
    String version
    String packageAsString

    ArchetypeModuleOption(String asString) {
        if (asString == null || asString.isEmpty()) {
            asString = DEFAULT_VALUE
        }
        def (groupId, artifactId, version, packageAsString, _heckHeckSKipped) = "$asString:heckHeck".split(":")

        if (version == null || version.isEmpty()) {
            version = DEFAULT_VERSION
        }

        if (packageAsString == null || packageAsString.isEmpty()) {
            packageAsString = "$groupId.$artifactId".replaceAll("-", "_").toLowerCase()
        }

        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
        this.packageAsString = packageAsString
    }

    String toReadableString() {
        "$groupId:$artifactId:$version:$packageAsString"
    }
}

class MavenService {

    private static final String mavenNamespace = "http://maven.apache.org/POM/4.0.0"
    private def parser = new XmlParser()

    Global global

    MavenService(Global global) {
        this.global = global
    }

    def createFromArchetype(MvnArchetype archetype, ArchetypeModuleOption options) {
        println "creating a new maven module from archetype ${archetype.toReadableString()} a project ${options.toReadableString()}"
        Utils.executeCLI("""mvn archetype:generate -B 
    -DarchetypeGroupId=${archetype.groupId} 
    -DarchetypeArtifactId=${archetype.artifactId} 
    -DarchetypeVersion=${archetype.version}
    -DgroupId=${options.groupId} -DartifactId=${options.artifactId} -Dversion=${options.version} -Dpackage=${options.packageAsString}
""")
    }

    def addNodeIfNotExist(parsed, node) {
        if (parsed[node].isEmpty()) {
            parser.createNode(
                    parsed,
                    node,
                    [:]
            )
        }
    }

    def addWrapper(File currentDirectory) {
        println "adding a wrapper to a project"
        Utils.executeCLI("mvn -N io.takari:maven:wrapper")
    }

    def makeUberjar(File file) {
        def text = '''
            <plugin>
                <artifactId>maven-assembly-plugin</artifactId>

                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>org.markjay.Application</mainClass>
                        </manifest>
                    </archive>
                    <descriptorRefs>
                        <descriptorRef>jar-with-dependencies</descriptorRef>
                    </descriptorRefs>
                </configuration>

                <executions>
                    <execution>
                        <id>make-assembly</id> <!-- this is used for inheritance merges -->
                        <phase>package</phase> <!-- bind to the packaging phase -->
                        <goals>
                            <goal>single</goal>
                        </goals>
                    </execution>
                </executions>

            </plugin>
'''
        addPlugin(file, text)
    }

    private def addPlugin(File file, String pluginNodeXmlAsString) {
        def content = file.text

        def parsed = parser.parseText(content)

        addNodeIfNotExist(parsed, newTag("build"))
        addNodeIfNotExist(parsed.build[0], newTag("plugins"))


        def pluginXml = parser.parseText(pluginNodeXmlAsString)

        def pluginArtifactId = pluginXml.artifactId.text()

        def pluginFound = parsed.build.plugins.plugin.findAll {
            it.artifactId.text() == pluginArtifactId
        }

        if (pluginFound) {
            println "Already found a plugin '$pluginArtifactId', skipping this part"
        } else {
            println "Adding plugin '$pluginArtifactId'"
            parsed.build[0].plugins[0].children().add(pluginXml)

            def resultAsText = XmlUtil.serialize(parsed)

            file.write resultAsText
        }
    }

    def addDependency(Dependency dependency, File file) {
        def content = file.text

        def parsed = parser.parseText(content)

        addNodeIfNotExist(parsed, newTag("dependencies"))

        def dependencyXml = parser.parseText(dependency.toMavenString())

        def dependencyFound = parsed.dependencies.dependency.findAll {
            it.artifactId.text() == dependency.artifactId && it.groupId.text() == dependency.groupId
        }

        if (dependencyFound) {
            println "Already found a dependency '$dependency', skipping this part"
        } else {
            println "Adding a dependency '${dependency.toReadableString()}'"
            parsed.dependencies[0].children().add(dependencyXml)

            def resultAsText = XmlUtil.serialize(parsed)

            file.write resultAsText
        }
    }

    private QName newTag(String tagName) {
        new QName(mavenNamespace, tagName)
    }
}

// -------------------------------------------------------------------------------------------------------------------
// main

@SourceURI URI _sourceUri

@Option(names= ["-h", "--help"], usageHelp= true, description= "Show this help message and exit.")
@Field boolean helpRequested

@Option(names= ["-g", "--generate-autocomplete"], description= "Generate autocomplete file and exit.")
@Field boolean autocompleteRequest

// -------------------------------------------------------------------------------------------------------------------
// autocomplete

if (autocompleteRequest) {
    println _sourceUri
    paths = new Global(_sourceUri)
    String completionFileName = paths.bstrapCompletionPath()
    println "generating autocompletion script $completionFileName"
    String[] params = ["-f", "-o", completionFileName, getClass().canonicalName]
    picocli.AutoComplete.main(params)
    def content = new File("$completionFileName").text
    new File("$completionFileName").write content.replaceAll(
            "complete -F _complete_bstrap -o default bstrap bstrap.sh bstrap.bash",
            "complete -F _complete_bstrap -o default bstrap bstrap.groovy bstrap.sh bstrap.bash"
    )
    println "Autocomplete script generation finished. Source it like this: \n" +
            ". ${completionFileName}"
    return 0
}



// -------------------------------------------------------------------------------------------------------------------
// main

@Command(name = "mvn-generate", description = "generates a new maven project from an archetype")
public class MvnGenerate implements Runnable {

    @ParentCommand
    private Object parent; // picocli injects reference to parent command

    // used option instead of parameter because autocomplete does not work well with subcommands and @Parameters
    @Option(names= ["-a", "--archetype"],
            description= "archetype to use to generate a project from. Default values = \${DEFAULT-VALUE}, possible value =  \${COMPLETION-CANDIDATES}")
    private PredefinedArchetype archetype = PredefinedArchetype.simpleJar

    @SourceURI
    URI sourceUri

    @Override
    void run() {
//        "mvn archetype:generate -DarchetypeGroupId=${archetype.groupId} " +
//                "-DarchetypeArtifactId=${archetype.artifactId} " +
//                "-DarchetypeVersion=${archetype.version}".execute()
        println "hey"
    }
}

@Command(name = "add", description = "adds stuff to a maven project")
public class Add implements Runnable {

    @ParentCommand
    private Object parent; // picocli injects reference to parent command

    @SourceURI
    URI sourceUri

    @Option(names= ["-n", "--new-module-from-archetype"],
            description= "creates a new module from ")
    private MvnArchetype newModuleFromArchetype

    @Option(names= ["-o", "--new-module-options"],
            description= """
Should be used with -n option. Sets option for generation from archetype in a form of:
groupId:archetypeId:version:package
for example:
com.company:project:1.0-SNAPSHOT:com.company.project
if version is omitted 1.0-SNAPSHOT will be used 
if package is omitted groupId.artifactId will be used(replace '-' to '_' and lowercasing it)
if this parameter is not passed, com.company:project:1.0-SNAPSHOT:com.company.project will be used
""", defaultValue = "com.company:project:1.0-SNAPSHOT:com.company.project")
    private String newModuleOptions

    @Option(names= ["-a", "--add-attributes"],
            description= "Adds gitattributes to the project", defaultValue = "false")
    private boolean addAttributes

    @Option(names= ["-i", "--add-gitignore"],
            description= "Adds .gitignore to the project", defaultValue = "false")
    private boolean addIgnore

    @Option(names= ["-e", "--add-editorconfig"],
            description= "Adds editorconfig to the project", defaultValue = "false")
    private boolean addEditorConfig

    @Option(names= ["-u", "--make-uberjar"],
            description= "Adds uberjar plugin to the pom", defaultValue = "false")
    private boolean makeUberJar

    @Option(names= ["-w", "--add-mvn-wrapper"],
            description= "Adds maven wrapper", defaultValue = "false")
    private boolean addMvnWrapper


    @Option(names= ["-d", "--add-dependency"], description= "Adds a specific dependency to the project")
    private Set<Dependency> dependencies

    @Override
    void run() {
        def mavenService = new MavenService(new Global(sourceUri))
        def gitService = new GitService()
        def editorConfigService = new EditorConfigService()

        def currectDirectory = new File(".")
        def pomFile = new File("./pom.xml")

        // TODO, not working right now correctly
        if (newModuleFromArchetype != null) {
            if (pomFile.exists()) {
                throw new RuntimeException("request to create a new project while pom.xml already exists in the working directory, aborting")
            }
            mavenService.createFromArchetype(newModuleFromArchetype, new ArchetypeModuleOption(newModuleOptions))
        }

        if (addAttributes) {
            gitService.addAttributes(currectDirectory)
        }

        if (addIgnore) {
            gitService.addIgnore(currectDirectory)
        }

        if (makeUberJar){
            mavenService.makeUberjar(pomFile)
        }

        if (addEditorConfig) {
            editorConfigService.addEditorConfig(currectDirectory)
        }

        if (addMvnWrapper) {
            mavenService.addWrapper(currectDirectory)
        }

        if (dependencies != null) {
            dependencies.forEach {
                mavenService.addDependency(it, pomFile)
            }
        }

        // TODO area

//        "mvn archetype:generate -DarchetypeGroupId=${archetype.groupId} " +
//                "-DarchetypeArtifactId=${archetype.artifactId} " +
//                "-DarchetypeVersion=${archetype.version}".execute()
        println "done"
    }
}
