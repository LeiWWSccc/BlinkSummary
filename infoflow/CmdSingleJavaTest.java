package soot.jimple.infoflow;

import soot.jimple.infoflow.source.DefaultSourceSinkManager;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * 简单、测试单个java程序，仅供测试使用，发布请删除
 *
 * @author wanglei
 */
public class CmdSingleJavaTest {


    public static void main(String[] args) {
        System.out.println("Simple Java test Runner!");

        try {

            // predefine sources and sinks
            Collection<String> sources = new ArrayList<>();
            Collection<String> sinks = new ArrayList<>();
            sources.add("<TaintTest: java.lang.String parsePwd(java.lang.String[])>");
            sources.add("<TaintTest: int parsePwd(java.lang.String[])>");
            sources.add("<TaintTest: java.lang.String parseId(java.lang.String[])>");
            sources.add("<TaintTest: java.lang.String parseId()>");
            sources.add("<TaintTest: java.lang.String parseName(java.lang.String[])>");
            sources.add("<TaintTest: java.lang.String parseName1(java.lang.String[])>");
            sources.add("<TaintTest: java.lang.String parseName2(java.lang.String[])>");
            sinks.add("<TaintTest: void sink(java.lang.String)>");
            sinks.add("<TaintTest: void sink(int)>");
            ISourceSinkManager sourcesSinks = new DefaultSourceSinkManager(sources, sinks);


            // the set of classes that shall be checked for taint analysis
            Collection<String> classes = new ArrayList<>();
            classes.add("TaintTest");

            //lib path
            String libpath = "";
            // process dir path

            //static test
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/statictest/test0b1c1/";


            //summarytest
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/summarytest/test1b1/";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/summarytest/testinjectb1c1/";

            //arraytest
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/arraytest/test1b1/";


            //aliascalltest:
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliascalltest/test1b1c2/";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliascalltest/test0b3c3/";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliascalltest/test0b4c1/";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliascalltest/test1b1c1/";
            //alias Opt :
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliasOpt/test5/";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliasOpt/testB3/";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliasOpt/testB32/";

            //next test:
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/nexttest/test2";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/nexttest/testcall";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/nexttest/testmerge2";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/nexttest/test2sink";

            //normal test
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/normaltest/test1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/normaltest/test1b1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/normaltest/test1b1sp";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/normaltest/test1b1while1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/normaltest/testop1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/normaltest/test6";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/normaltest/testrightleft";

            //call test
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/testparma1b3c1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/testthis";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/test1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/teststatic1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/testmerge";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/multiparmtest3";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/testex2";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/testmanycall2";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/testmanycall2";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/calltest/testrecur";

            //single test
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/singletest/calltest";

            //alias test
            String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliastest/test1b1c1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliastest/test0b1c1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliastest/testthis1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliastest/testmerge";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliastest/testnewobject";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/aliastest/test1if";

            //interalias test
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/interaliastest/test5";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/interaliastest/singletest2";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/interaliastest/singletestcall";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/interaliastest/testunb";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/interaliastest/singletestsumopt";

            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/interaliastest/singletest2";

            // test ubr

            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/ubrtest/testunb3";
            // String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/ubrtest/testunb4";

            //return test:
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/returntest/testreturn";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/returntest/thistest";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/interaliastest/singletestcall";

            //end test:
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/endtest/test1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/endtest/testmulti";
            //wrapper test
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/wrappertest/test1";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/wrappertest/testB3";
            //array test
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/arraytest/singletest";
            //droidbench test :
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/droidbench/test1";
            //opt test :
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/opttest/singletest";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/opttest/multitest";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/opttest/calltest";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/opttest/calltest1eff";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/opttest/ifcalltest";

            //summary opt
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/opttest/sumtest";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/opttest/sumtestif";
            //String appPath = "/Users/wanglei/Desktop/MyProgram/MultiTest/src/main/java/sumopttest/test4";


            final ITaintPropagationWrapper taintWrapper;
            final EasyTaintWrapper easyTaintWrapper;
            File twSourceFile = new File("../soot-infoflow/EasyTaintWrapperSource.txt");
            if (twSourceFile.exists())

                easyTaintWrapper = new EasyTaintWrapper(twSourceFile);

            else {
                twSourceFile = new File("EasyTaintWrapperSource.txt");
                if (twSourceFile.exists())
                    easyTaintWrapper = new EasyTaintWrapper(twSourceFile);
                else {
                    System.err.println("Taint wrapper definition file not found at "
                            + twSourceFile.getAbsolutePath());
                    return ;
                }
            }
            boolean aggressiveTaintWrapper = false;
            easyTaintWrapper.setAggressiveMode(aggressiveTaintWrapper);
            taintWrapper = easyTaintWrapper;


            Infoflow infoflow = new Infoflow();
            infoflow.setTaintWrapper(taintWrapper);

            infoflow.computInfoflowForTest(appPath, libpath, classes, sourcesSinks);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
