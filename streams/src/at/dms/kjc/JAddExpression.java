/*
 * Copyright (C) 1990-2001 DMS Decision Management Systems Ges.m.b.H.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id: JAddExpression.java,v 1.11 2006-10-27 20:48:54 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;
import at.dms.compiler.UnpositionedError;

/**
 * This class implements '+ - * /' specific operations
 * Plus operand may be String, numbers
 */
public class JAddExpression extends JBinaryArithmeticExpression {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    protected JAddExpression() {} // for cloner only

    /**
     * Construct a node in the parsing tree
     * This method is directly called by the parser
     * @param   where       the line of this node in the source code
     * @param   left        the left operand
     * @param   right       the right operand
     */
    public JAddExpression(TokenReference where,
                          JExpression left,
                          JExpression right)
    {
        super(where, left, right);
    }

    public JAddExpression(JExpression left,
                          JExpression right) {
        this(null, left, right);
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
        StringBuffer    buffer = new StringBuffer();

        buffer.append("JAddExpression[");
        buffer.append(left.toString());
        buffer.append(", ");
        buffer.append(right.toString());
        buffer.append("]");
        return buffer.toString();
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Analyses the expression (semantically).
     * @param   context     the analysis context
     * @return  an equivalent, analysed expression
     * @exception   PositionedError the analysis detected an error
     */
    public JExpression analyse(CExpressionContext context) throws PositionedError {
        left = left.analyse(context);
        right = right.analyse(context);
        check(context, left.getType() != CStdType.Void && right.getType() != CStdType.Void,
              KjcMessages.ADD_BADTYPE, left.getType(), right.getType());

        check(context, !(left instanceof JTypeNameExpression) && !(right instanceof JTypeNameExpression),
              KjcMessages.ADD_BADTYPE, left.getType(), right.getType());

        try {
            type = computeType(left.getType(), right.getType());
        } catch (UnpositionedError e) {
            throw e.addPosition(getTokenReference());
        }

        // programming trick: no conversion for strings here: will be done in code generation
        if (!type.equals(CStdType.String)) {
            left = left.convertType(type, context);
            right = right.convertType(type, context);
        }

        if (left.isConstant() && right.isConstant()) {
            if (type.equals(CStdType.String)) {
                // in this case we have to convert the operands
                left = left.convertType(type, context);
                right = right.convertType(type, context);
                return new JStringLiteral(getTokenReference(), left.stringValue() + right.stringValue());
            } else {
                return constantFolding();
            }
        } else {
            return this;
        }
    }

    /**
     * compute the type of this expression according to operands
     * @param   leftType        the type of left operand
     * @param   rightType       the type of right operand
     * @return  the type computed for this binary operation
     * @exception   UnpositionedError   this error will be positioned soon
     */
    public static CType computeType(CType   leftType, CType rightType) throws UnpositionedError {
        if(rightType==null)
            return leftType;
        if(leftType==null)
            return rightType;
        if (leftType.equals(CStdType.String)) {
            if (rightType == CStdType.Void) {
                throw new UnpositionedError(KjcMessages.ADD_BADTYPE, leftType, rightType);
            }
            return CStdType.String;
        } else if(rightType.equals(CStdType.String)) {
            if (leftType == CStdType.Void) {
                throw new UnpositionedError(KjcMessages.ADD_BADTYPE, leftType, rightType);
            }
            return CStdType.String;
        } else {
            if (leftType.isNumeric() && rightType.isNumeric()) {
                return CNumericType.binaryPromote(leftType, rightType);
            }

            throw new UnpositionedError(KjcMessages.ADD_BADTYPE, leftType, rightType);
        }
    }

    public CType getType() {
        try {
            return computeType(left.getType(),right.getType());
        } catch(UnpositionedError e) {
            return super.getType();
        }
    }

    // ----------------------------------------------------------------------
    // CONSTANT FOLDING
    // ----------------------------------------------------------------------

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    public int compute(int left, int right) {
        return left + right;
    }

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    public long compute(long left, long right) {
        return left + right;
    }

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    public float compute(float left, float right) {
        return left + right;
    }

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    public double compute(double left, double right) {
        return left + right;
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Accepts the specified visitor
     * @param   p       the visitor
     */
    public void accept(KjcVisitor p) {
        p.visitBinaryExpression(this, "+", left, right);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    public Object accept(AttributeVisitor p) {
        return p.visitBinaryExpression(this, "+", left, right);
    }

    /**
     * Accepts the specified visitor
     * @param p the visitor
     * @param o object containing extra data to be passed to visitor
     * @return object containing data generated by visitor 
     */
    @Override
    public <S,T> S accept(ExpressionVisitor<S,T> p, T o) {
        return p.visitAdd(this,o);
    }


    /**
     * @param   type        the type of result
     * @return  the type of opcode for this operation
     */
    public static int getOpcode(CType type) {
        switch (type.getTypeID()) {
        case TID_FLOAT:
            return opc_fadd;
        case TID_LONG:
            return opc_ladd;
        case TID_DOUBLE:
            return opc_dadd;
        default:
            return opc_iadd;
        }
    }

    /**
     * Generates JVM bytecode to evaluate this expression.
     *
     * @param   code        the bytecode sequence
     * @param   discardValue    discard the result of the evaluation ?
     */
    public void genCode(CodeSequence code, boolean discardValue) {
        setLineNumber(code);

        if (type.equals(CStdType.String)) {
            code.plantClassRefInstruction(opc_new, "java/lang/StringBuffer");
            code.plantNoArgInstruction(opc_dup);
            code.plantMethodRefInstruction(opc_invokespecial,
                                           "java/lang/StringBuffer",
                                           JAV_CONSTRUCTOR,
                                           "()V");
            appendToStringBuffer(code, left);
            appendToStringBuffer(code, right);
            code.plantMethodRefInstruction(opc_invokevirtual,
                                           "java/lang/StringBuffer",
                                           "toString",
                                           "()Ljava/lang/String;");
        } else {
            left.genCode(code, false);
            right.genCode(code, false);

            code.plantNoArgInstruction(getOpcode(getType()));
        }

        if (discardValue) {
            code.plantPopInstruction(getType());
        }
    }

    /**
     * Generates a sequence of bytescodes
     * @param   code        the code list
     */
    private void appendToStringBuffer(CodeSequence code, JExpression expr) {
        if ((expr instanceof JAddExpression) && (expr.getType().equals(CStdType.String))) {
            ((JAddExpression)expr).appendToStringBuffer(code, ((JAddExpression)expr).left);
            ((JAddExpression)expr).appendToStringBuffer(code, ((JAddExpression)expr).right);
        } else {
            CType   type = expr.getType();

            expr.genCode(code, false);
            if (!type.isReference() || type.equals(CStdType.String)) {
                // StringBuffer.append() is defined for most primitive types and
                // for type String. StringBuffer.append() is not defined for byte
                // and short ; using StringBuffer.append(int) instead is safe
                // since the value pushed on the stack is of type int and
                // should be interpreted as int for these types.
                if (type == CStdType.Byte || type == CStdType.Short) {
                    type = CStdType.Integer;
                }

                code.plantMethodRefInstruction(opc_invokevirtual,
                                               "java/lang/StringBuffer",
                                               "append",
                                               "(" + type.getSignature() + ")Ljava/lang/StringBuffer;");
            } else {
                code.plantMethodRefInstruction(opc_invokevirtual,
                                               "java/lang/StringBuffer",
                                               "append",
                                               "(Ljava/lang/Object;)Ljava/lang/StringBuffer;");
            }
        }
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.kjc.JAddExpression other = new at.dms.kjc.JAddExpression();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JAddExpression other) {
        super.deepCloneInto(other);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
