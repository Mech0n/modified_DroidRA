package yisumi.booster;

import java.util.ArrayList;
import java.util.List;

import yisumi.ClassDescription;
import yisumi.model.SimpleStmtValue;
import yisumi.model.StmtKey;
import yisumi.model.StmtType;
import yisumi.model.UniqStmt;
import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.javaToJimple.DefaultLocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.Stmt;

public class ClassNewInstanceCallInstrumentation extends DefaultInstrumentation {
    public ClassNewInstanceCallInstrumentation(StmtKey stmtKey,
                                               SimpleStmtValue stmtValue, UniqStmt uniqStmt) {
        super(stmtKey, stmtValue, uniqStmt);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void instrument() {
        SootMethod sootMethod = stmtKey.getMethod();
        Stmt stmt = stmtKey.getStmt();

        Body body = sootMethod.retrieveActiveBody();

        Stmt nextStmt = getNextStmt(body, stmt);

        DefaultLocalGenerator localGenerator = new DefaultLocalGenerator(body);

        if (StmtType.CLASS_NEW_INSTANCE != stmtValue.getType()) {
            return;
        }

        List<Unit> injectedUnits = new ArrayList<Unit>();

        //for (ClassDescription clsDesc : stmtValue.getClsSet())
        //{
        ClassDescription clsDesc = stmtValue.getClsDesc();
        SootClass sc = Scene.v().getSootClass(clsDesc.name);


        Local local = localGenerator.generateLocal(sc.getType());

        Unit newU = Jimple.v().newAssignStmt(local, Jimple.v().newNewExpr(sc.getType()));
        injectedUnits.add(newU);

        List<SootMethod> cinitList = InstrumentationUtils.getMethodByName(sc, "<clinit>");
        if (cinitList.size() > 0) {
            SootMethod cinit = cinitList.get(0);

            Unit cinitCallU = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(cinit.makeRef()));
            injectedUnits.add(cinitCallU);
        }


        try {
            SootMethod init = sc.getMethod("void <init>()");
            InvokeExpr invokeExpr = Jimple.v().newVirtualInvokeExpr(local, init.makeRef());
            Unit initU = Jimple.v().newInvokeStmt(invokeExpr);
            injectedUnits.add(initU);

            AssignStmt assignStmt = (AssignStmt) stmt;
            Unit assignU = Jimple.v().newAssignStmt(assignStmt.getLeftOp(), local);
            injectedUnits.add(assignU);

        } catch (Exception ex) {
            //It does not make sense to come here.
            System.out.println("There is no <init> method for class " + sc.getName());
        }
        //}

        for (int i = injectedUnits.size() - 1; i >= 0; i--) {
            body.getUnits().insertAfter(injectedUnits.get(i), stmt);
        }

        injectedStmtWrapper(body, localGenerator, stmt, nextStmt);

        System.out.println(body);
        body.validate();
    }

}