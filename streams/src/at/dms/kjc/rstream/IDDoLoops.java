package at.dms.kjc.rstream;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import java.util.ListIterator;
import at.dms.kjc.flatgraph.*;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This pass identifies java-style for loop that can be converted to 
 * fortran-style do loops. It should be run right before code generation
 * so that no other pass alters the for loops that are recognized and thus
 * invalidates the classification.
 *
 * @author Michael Gordon
 */

public class IDDoLoops extends SLIREmptyVisitor implements FlatVisitor, Constants
{
    //
    private int forLevel = 0;
    
    /**
     * The entry point of this class, given a stream <top> and 
     * everything downstream of it, classify the for loop in each method of
     * each filter as to whether they can be converted to do loops.
     *
     * @param top The top level of the application
     *
     */

    public static void doit(FlatNode top)
    {
	IDDoLoops doLoops = new IDDoLoops();
	top.accept(doLoops, null, true);
    }

    /**
     * Visit a flat node and iterate over all the methods 
     * if this is a filter flat node and check for 
     * do loops.
     *
     * @param node current flat node we are visiting
     *
     */

    public void visitNode(FlatNode node) 
    {
	if (node.isFilter()) {
	    SIRFilter filter = (SIRFilter)node.contents;
	    //iterate over the methods to check for a comm. exp.
	    JMethodDeclaration[] methods = filter.getMethods();
	    for (int i = 0; i < methods.length; i++) {
		//iterate over the statements
		for (ListIterator it = methods[i].getStatementIterator();
		     it.hasNext(); ){
		    ((JStatement)it.next()).accept(this);
		    assert this.forLevel == 0;
		}
	    }
	}
    }
    
    
    private IDDoLoops() 
    {
	forLevel = 0;
    }
    
    /**
     * See comments in method.
     */
    public void visitForStatement(JForStatement self,
				  JStatement init,
				  JExpression cond,
				  JStatement incr,
				  JStatement body) {
	JLocalVariable induction = null;
	//put the do loop information in this class...
	DoLoopInformation info = new DoLoopInformation();
	//we are inside a for loop
	forLevel++;
	
	System.out.println("----------------");
	if (init != null) {
	    JExpression initExp = getExpression(init);
	    //JAssignment Expression 
	    
	    //get the induction variable, simple calculation
	    getInductionVariable(initExp, info);
	    //check for method calls
	    if (info.init != null && CheckForMethodCalls.check(info.init))
		info.init = null;
	    System.out.println("Induction Var: " + info.induction);
	    System.out.println("init exp: " + info.init);
	}
	if (cond != null && info.induction != null && info.init != null) {
	    //get the condition statement and put it in the correct format
	    getDLCondExpression(passThruParens(cond), info);
	    //check for method calls in condition
	    if (info.cond != null && CheckForMethodCalls.check(info.cond))
		info.cond = null;
	    System.out.println("cond exp: " + info.cond);
	    //cond.accept(this);
	}
	//check the increment, only if there is one and we have passed 
	//everything above
	if (incr != null && info.induction != null && info.init != null &&
	    info.cond != null) {
	    //get the increment expression, and put it in the right format
	    getDLIncrExpression(getExpression(incr), info);
	    //check for method call in increment
	    if (info.incr != null && CheckForMethodCalls.check(info.incr))
		info.incr = null;
	    System.out.println("incr exp: " + info.incr);
	    //incr.accept(this);
	}

	//check that we found everything as expected...
	if (info.init != null && info.induction != null && info.cond != null &&
	    info.incr != null) {
	    //the induction variable or any variables accessed in the cond or
	    //increment are accessed in the body, and check for function calls
	    //if their are fields or structures that included in this list of
	    //variables
	    CheckLoopBody checkBody = new CheckLoopBody(info);
	    checkBody.check(body);
	}
	
	//check for nested for loops that can be converted...
	body.accept(this);
	
	//leaving for loop
	forLevel--;
	assert forLevel >= 0;
    }
    

    /**
     * Calculate the increment expression for the do loop and 
     * put it in the right format, so just return <exp> where
     * ind-var += <exp>.
     *
     * @param incrExp The orginal for loop increment expression
     * @param info The information on this do loop, place the 
     *  do loop increment expression in here.
     *
     */

    private void getDLIncrExpression(JExpression incrExp, 
				     DoLoopInformation info)
    {
	if (incrExp instanceof JBinaryExpression) {
	    if (incrExp instanceof JCompoundAssignmentExpression) {
		JCompoundAssignmentExpression comp = 
		    (JCompoundAssignmentExpression)incrExp;
		if (comp.getLeft() instanceof JLocalVariableExpression &&
		    ((JLocalVariableExpression)comp.getLeft()).getVariable().equals(info.induction)) {
		    if (comp.getOperation() == OPE_PLUS) {
			info.incr = comp.getRight();
		    } else if (comp.getOperation() == OPE_MINUS) {
			info.incr = new JUnaryMinusExpression(null, comp.getRight());
		    }
		}
	    }
	    else if (incrExp instanceof JAssignmentExpression) {
		JAssignmentExpression ass = (JAssignmentExpression)incrExp;
		if (ass.getRight() instanceof JAddExpression ||
		    ass.getRight() instanceof JMinusExpression) {
		    JBinaryExpression bin = (JBinaryExpression)ass.getRight();
		    //check that the left is an access to the induction variable
		    if (bin.getLeft() instanceof JLocalVariableExpression &&
			((JLocalVariableExpression)bin.getLeft()).getVariable().equals(info.induction)) {
			if (ass.getRight() instanceof JAddExpression)
			    info.incr = bin.getRight();
			if (ass.getRight() instanceof JMinusExpression)
			    info.incr = new JUnaryMinusExpression(null, bin.getRight());
		    }
		    if (bin.getRight() instanceof JLocalVariableExpression &&
			((JLocalVariableExpression)bin.getRight()).getVariable().equals(info.induction)) {
			if (ass.getRight() instanceof JMinusExpression)
			    info.incr = bin.getLeft();
			if (ass.getRight() instanceof JMinusExpression)
			    info.incr = new JUnaryMinusExpression(null, bin.getLeft());
		    }
		}
	    }
	}
	else if (incrExp instanceof JPrefixExpression) {
	    JPrefixExpression pre = (JPrefixExpression)incrExp;
	    //check that we assigning the induction variable
	    if (pre.getExpr() instanceof JLocalVariableExpression &&
		((JLocalVariableExpression)pre.getExpr()).getVariable().equals(info.induction)) {
		if (pre.getOper() == OPE_PREINC) {
		    info.incr = new JIntLiteral(1);
		} else {
		    info.incr = new JIntLiteral(-1);
		}		
	    }
	}
	else if (incrExp instanceof JPostfixExpression) {
	    JPostfixExpression post = (JPostfixExpression)incrExp;
	    //check that we assigning the induction variable
	    if (post.getExpr() instanceof JLocalVariableExpression &&
		((JLocalVariableExpression)post.getExpr()).getVariable().equals(info.induction)) {
		if (post.getOper() == OPE_POSTINC) {
		    info.incr = new JIntLiteral(1);
		} else {
		    info.incr = new JIntLiteral(-1);
		}		
	    }
	}
	
    }
    

    private void getDLCondExpression(JExpression condExp,
				    DoLoopInformation info) 
    {
	if (condExp instanceof JRelationalExpression) {
	    JRelationalExpression cond = (JRelationalExpression)condExp;
	    
	    //make sure that lhs is an access to the induction variable 
	    if (cond.getLeft() instanceof JLocalVariableExpression &&
		((JLocalVariableExpression)cond.getLeft()).getVariable().equals(info.induction)) {
		
		//rhs is of type int
		switch (cond.getOper()) {
		case OPE_LT:
		    info.cond = new JMinusExpression(null, cond.getRight(),
						     new JIntLiteral(1));
		    break;
		case OPE_LE:
		    info.cond = cond.getRight();
		    break;
		case OPE_GT:
		    info.cond = cond.getRight();
		    break;
		case OPE_GE:
		    info.cond = new JAddExpression(null, cond.getRight(),
						   new JIntLiteral(1));
		    break;
		default:
		    assert false;
		}
	    }
	}
    }
    

    private void getInductionVariable(JExpression initExp, 
				      DoLoopInformation info) 
    {
	if (initExp instanceof JAssignmentExpression) {
	    JAssignmentExpression ass = (JAssignmentExpression)initExp;
	    
	    //check that we are dealing with integers 
	    if (!(ass.getLeft().getType().isOrdinal() && 
		  ass.getRight().getType().isOrdinal()))
		return;
	    
	    //check that the left is a variable expression
	    if (ass.getLeft() instanceof JLocalVariableExpression) {
		//set the induction variable
		info.induction = 
		    ((JLocalVariableExpression)ass.getLeft()).getVariable();
	    }
	    else 
		return;
	    
	    info.init = ass.getRight();
	}
    }
    

    public static JExpression getExpression(JStatement orig) 
    {
	if (orig instanceof JExpressionListStatement) {
	    JExpressionListStatement els = (JExpressionListStatement)orig;
	    if (els.getExpressions().length == 1)
		return passThruParens(els.getExpression(0));
	    else
		return null;
	}
	else if (orig instanceof JExpressionStatement) {
	    return passThruParens(((JExpressionStatement)orig).getExpression());
	}
	else 
	    return null;
    }
 
    public static JExpression passThruParens(JExpression orig) 
    {
	if (orig instanceof JParenthesedExpression) {
	    return passThruParens(((JParenthesedExpression)orig).getExpr());
	}
	return orig;
    }


    class CheckLoopBody extends SLIREmptyVisitor 
    {
	private DoLoopInformation info;
	private HashSet varsToCheck;
	private HashSet varsAssigned;
	private boolean hasFields;
	private boolean hasMethods;

	public boolean check(JStatement body)
	{
	    Iterator it;	
	    body.accept(this);
	    
	    if (hasFields && hasMethods)
		return false;
	    
	    it  = varsAssigned.iterator();
	    System.out.println("*** Vars assigned: ");
	    while (it.hasNext()) {
		Object cur = it.next();
		System.out.println("  " + cur);
	    }
	    System.out.println("*** Vars assigned.  ");

	    it = varsToCheck.iterator();
	    while (it.hasNext()) {
		Object var = it.next();
		if (varsAssigned.contains(var)) {
		    System.out.println("Cannot formulate do loop, var " + var + 
				       " assigned in loop");
		    return false;
		}
		
	    }
	    System.out.println("Body okay");
	    return true;
	}
	
	public CheckLoopBody(DoLoopInformation info) 
	{
	    this.info = info;
	    varsToCheck = new HashSet();
	    varsAssigned = new HashSet();
	    hasFields = false;
	    hasMethods = false;
	    findVarsToCheck();
	}
	
	private void findVarsToCheck() 
	{
	    //add the induction variable
	    varsToCheck.add(info.induction);
	    StrToRStream.addAll(varsToCheck, VariablesUsed.getVars(info.init));
	    StrToRStream.addAll(varsToCheck, VariablesUsed.getVars(info.cond));
	    StrToRStream.addAll(varsToCheck, VariablesUsed.getVars(info.incr));

	    Iterator it = varsToCheck.iterator();
	    while (it.hasNext()) {
		Object cur = it.next();
		if (cur instanceof String) 
		    hasFields = true;
		System.out.println(cur);
	    }
	    
	}
	

	public void visitAssignmentExpression(JAssignmentExpression self,
					      JExpression left,
					      JExpression right) {
	    
	    StrToRStream.addAll(varsAssigned, VariablesUsed.getVars(left));
	    right.accept(this);
	}

	public void visitCompoundAssignmentExpression(JCompoundAssignmentExpression self,
						      int oper,
						      JExpression left,
						      JExpression right) {
	    StrToRStream.addAll(varsAssigned, VariablesUsed.getVars(left));
	    right.accept(this);
	}
	
	public void visitPrefixExpression(JPrefixExpression self,
                                      int oper,
                                      JExpression expr) {
	    StrToRStream.addAll(varsAssigned, VariablesUsed.getVars(expr));
	}

	public void visitPostfixExpression(JPostfixExpression self,
					   int oper,
					   JExpression expr) {
	    StrToRStream.addAll(varsAssigned, VariablesUsed.getVars(expr));
	}
	
	public void visitMethodCallExpression(JMethodCallExpression self,
					      JExpression prefix,
					      String ident,
					      JExpression[] args) {
	    hasMethods = true;
	    for (int i = 0; i < args.length; i++) 
		args[i].accept(this);    
	}
    }
    
    

    class DoLoopInformation 
    {
	public JLocalVariable induction;
	public JExpression init;
	public JExpression cond;
	public JExpression incr;

	public DoLoopInformation() 
	{
	    induction = null;
	    init = null;
	    cond = null;
	    incr = null;
	}
    }    
}