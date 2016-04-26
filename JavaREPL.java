import com.sun.source.util.JavacTask;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class JavaREPL {

    private static Stack symbol = new Stack();
    private static ArrayList declaration = new ArrayList();
    private static ArrayList statement = new ArrayList();
    private static int validcode = 0;
    private static String declarationorstatement = "";
    private static int havetoprint = 0;
    private static int classcnt = 0;
    private static int classable = 0;
    private static ArrayList<String> FL = new ArrayList<String>();
    public static void deleteFolder(File dir) {
        File filelist[]=dir.listFiles();
        int listlen=filelist.length;
        for(int i=0;i<listlen;i++) {
            if(filelist[i].isDirectory()) {
                deleteFolder(filelist[i]);
            }
            else {
                filelist[i].delete();
            }
        }
        dir.delete();
    }
    public static void main(String[] args) throws IOException, NoSuchMethodException {
        new File("tmp").mkdir();
        try {
            System.out.print("> ");
            Scanner cin = new Scanner(System.in);
            String ni;// = cin.nextLine();
            //while (ni != null) {
            while (cin.hasNextLine()) {
                ni = cin.nextLine();
                parse(ni);
                if(symbol.empty())
                System.out.print("> ");
            }
        }
        finally {
            int end = FL.size();
            for(int i=0; i<end; i++) {
                Path original = Paths.get(FL.get(i) + ".class");
                Path destination = Paths.get("tmp/"+FL.get(i)+".class");
                Files.move(original, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteFolder(new File("tmp"));
        }
    }

    public static void parse(String string) throws NoSuchMethodException, IOException {
        if(string.startsWith("//")) ;
        else if (string.equals("\r\n")) return;
        else if(string.startsWith("print")) {
            string = string.replaceFirst("print", "System.out.println(");
            havetoprint = 1;
        }
        declarationorstatement += string;
        symbol.clear();
        for(int i=0; i<declarationorstatement.length(); i++) {
            char ch = declarationorstatement.charAt(i);
            if(ch=='(' && validcode==0) symbol.push('(');
            else if(ch=='[' && validcode==0) symbol.push('[');
            else if(ch=='{' && validcode==0) symbol.push('{');
            else if(ch=='"'&&i>=1&&declarationorstatement.charAt(i-1)!='\\') {
                validcode = (validcode+1)%2;//******
                if(havetoprint==1&&(i==declarationorstatement.length()-1)) declarationorstatement+=")";
            }
            else if(ch==';'&&validcode==0&&symbol.empty()) {
                if(isDeclaration(declarationorstatement.substring(0, i))) {
                    if(havetoprint==1) {
                        String print = declarationorstatement.substring(0, i-1);
                        print += ");";
                        declaration.add(print);
                        String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                        declarationorstatement = righthalf;
                        havetoprint = 0;
                    }
                    else {
                        String print = declarationorstatement.substring(0, i+1);
                        declaration.add(print);
                        String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                        declarationorstatement = righthalf;
                    }
                    i=-1;
                }
                else {
                    if(havetoprint==1) {
                        String print = declarationorstatement.substring(0, i+1);
                        statement.add(print);
                        String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                        declarationorstatement = righthalf;
                        havetoprint = 0;
                    }
                    else {
                        statement.add(declarationorstatement.substring(0, i+1));
                        String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                        declarationorstatement = righthalf;
                    }
                    i=-1;
                }
            }
            else if(ch==';'&&validcode==0&&symbol.size()>=1&&havetoprint==1) {
                String print = declarationorstatement.substring(0, i);
                print += ");";
                String tmp = declarationorstatement.substring(0, i+1);
                declarationorstatement = declarationorstatement.replace(tmp, print);
                symbol.clear();
                havetoprint = 0;
                i = -1;
            }
            else if( (ch==']' || ch==')' || ch=='}' )&& !symbol.empty() )  {
                symbol.pop();
                if(i != declarationorstatement.length()-1) continue;
                if(symbol.empty()) {
                    if(isDeclaration(declarationorstatement.substring(0, i+1))) {
                        declaration.add(declarationorstatement.substring(0, i+1));
                        String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                        declarationorstatement = righthalf;
                        i=-1;
                    }
                    else {
                        statement.add(declarationorstatement.substring(0, i+1)) ;
                        String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                        declarationorstatement = righthalf;
                        i=-1;
                    }
                }
            }
           else if(i == declarationorstatement.length()-1 && symbol.empty()) {
                if(isDeclaration(declarationorstatement.substring(0, i+1))) {
                    declaration.add(declarationorstatement.substring(0, i+1));
                    String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                    declarationorstatement = righthalf;
                    i=-1;
                }
                else {
                    statement.add(declarationorstatement.substring(0, i+1)) ;
                    String righthalf = declarationorstatement.substring(i+1, declarationorstatement.length());
                    declarationorstatement = righthalf;
                    i=-1;
                }
                classable = 0;
            }
            else if(i == declarationorstatement.length()-1 && !symbol.empty()) {
                  classable = 1;
            }

        }
        if(declarationorstatement.length()==0) {
            int declarationNum = declaration.size();
            int statementNum = statement.size();
            for(int i=0; i<declarationNum; i++) {
                String tmpdeclatation = (String) declaration.get(i);
                String fileName = "Interp_"+ String.valueOf(classcnt);
                String name = fileName;
                if(classcnt!=0) fileName += (" extends Interp_"+ String.valueOf(classcnt-1));
                classcnt++;
                writeFile(name, fileName,tmpdeclatation, "");
            }
            for(int i=0; i<statementNum; i++) {
                String tmpstatement = (String) statement.get(i);
                String fileName = "Interp_"+String.valueOf(classcnt);
                String name = fileName;
                if(classcnt!=0) fileName += (" extends Interp_"+String.valueOf(classcnt-1));
                classcnt++;
                writeFile(name, fileName,"", tmpstatement);
            }
            statement.clear();
            declaration.clear();
        }
    }
    //code below come from http://docs.oracle.com/javase/7/docs/api/javax/tools/JavaCompiler.html
    //http://www.javabeat.net/the-java-6-0-compiler-api/
    public static boolean isDeclaration(String string) {
        String fileToCompile =
                "import java.io.*;\n" +
                "import java.util.*;\n" +
                "public class Bogus {\n" +
                "    public static decl;\n" +
                "    public static void exec() {\n" +
                "    }\n" +
                "}";
        fileToCompile = fileToCompile.replace("decl", string);
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.print(fileToCompile);
        out.close();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager stdFileManager=compiler.getStandardFileManager(null, Locale.getDefault(),null);
        //int compilationResult = compiler.run(null, null, null, fileToCompile);
        JavaFileObject file = new JavaSourceFromString("Bogus", writer.toString());
        Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(file);
        JavacTask task = (JavacTask) compiler.getTask(null, stdFileManager, diagnostics,  null, null, Arrays.asList(file));
        //JavaCompiler.CompilationTask task = compiler.getTask(null,stdFileManager,diagnostics,options,null,Arrays.asList(source));
        try {
            task.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return diagnostics.getDiagnostics().size() == 0;
    }
    public static void writeFile(String name, String fileName, String declaration, String statement) throws NoSuchMethodException, IOException {
        String fileContent =
        "import java.io.*;\r\n"+
        "import java.util.*;\r\n"+
        "public class A  {\r\n"+
            "//\r\n"+
            "public static void exec() { /**/\r\n"+
            "}\r\n"+
        "}\r\n";
        fileContent = fileContent.replace("A", fileName);
        if(declaration != "") declaration = "public static "+declaration;
        fileContent = fileContent.replace("//", declaration);
        fileContent = fileContent.replace("/**/", statement);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println(fileContent);
        out.close();
        StandardJavaFileManager stdFileManager=compiler.getStandardFileManager(null, Locale.getDefault(),null);
        stdFileManager.setLocation(StandardLocation.CLASS_PATH, Arrays.asList(new File(System.getProperty("user.dir"))));
        //int compilationResult = compiler.run(null, null, null, fileToCompile);
        JavaFileObject file = new JavaSourceFromString(name, writer.toString());
        Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(file);
        JavacTask task = (JavacTask) compiler.getTask(null, stdFileManager, diagnostics,  null, null, compilationUnits);
        boolean success = task.call();
        if(success==false) {
            List erroMessage = diagnostics.getDiagnostics();
            int num = erroMessage.size();
            System.out.print("> ");
            for(int i=0; i<num; i++) {
                String erroStr = String.valueOf(erroMessage.get(i));
                String[] tmpstr1 = erroStr.split("\n");
                for(int cnt=0; cnt<tmpstr1.length; cnt++) {
                    if(tmpstr1[cnt].startsWith("/")) {
                        String linenum = tmpstr1[cnt].split(":")[1];
                        String commend = tmpstr1[cnt].split(":")[3];
                        System.out.print("line " + String.valueOf(Integer.parseInt(linenum) + 1) + ":");
                        System.out.println(commend);
                    } else if(tmpstr1[cnt].startsWith("public") || tmpstr1[cnt].startsWith("   ") || tmpstr1[cnt].startsWith("}") ||
                            tmpstr1[cnt].contains("^")) {
                    } else {
                        System.out.println(tmpstr1[cnt]);
                    }
                }
            }
            classable =1;
            classcnt--;
            return;
        }
        // Getting the jar URL which contains target class
        URL[] classLoaderUrls = new URL[0];
        try {
            String url = System.getProperty("user.dir");
            classLoaderUrls = new URL[]{new URL("file:"+url+"/")};
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        // Create a new URLClassLoader
        URLClassLoader urlClassLoader = new URLClassLoader(classLoaderUrls);
        // Load the target class
        Class<?> beanClass = null;
        try {
            beanClass = urlClassLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
            e.printStackTrace();
        }
        // Create a new instance from the loaded class
        Constructor<?> constructor = beanClass.getConstructor();
        Object beanObj = null;
        try {
            beanObj = constructor.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        // Getting a method from the loaded class and invoke it
        Method method = beanClass.getMethod("exec");
        try {
            method.invoke(beanObj);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        FL.add(name);
    }
}
class JavaSourceFromString extends SimpleJavaFileObject {
    final String code;

    JavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),Kind.SOURCE);
        this.code = code;
    }
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}