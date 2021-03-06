/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.vxquery.compiler.rewriter.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.vxquery.compiler.rewriter.rules.util.OperatorToolbox;
import org.apache.vxquery.functions.BuiltinFunctions;
import org.apache.vxquery.functions.BuiltinOperators;

import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalExpressionTag;
import org.apache.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import org.apache.hyracks.algebricks.core.algebra.expressions.ExpressionAnnotationNoCopyImpl;
import org.apache.hyracks.algebricks.core.algebra.expressions.IExpressionAnnotation;
import org.apache.hyracks.algebricks.core.algebra.functions.AlgebricksBuiltinFunctions;
import org.apache.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import org.apache.hyracks.algebricks.core.algebra.functions.IFunctionInfo;
import org.apache.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

/**
 * The rule searches for where the XQuery function are used in place of Algebricks builtin function.
 * The combination of the boolean XQuery function and the XQuery equivalent function are replaced with
 * the Algebricks builtin function .
 *
 * <pre>
 * Before
 * 
 *   plan__parent
 *   %OPERATOR( $v1 : xquery_expression( \@input_expression ) )
 *   plan__child
 * 
 *   where xquery_expression has a known equivalent in Algebricks,
 *     such as conditional expressions and a check for null.
 *     The expression may include the boolean function to ensure only
 *     a true or false result.
 * 
 * After
 * 
 *   plan__parent
 *   %OPERATOR( $v1 : algebricks_function( \@input_expression ) )
 *   plan__child
 * </pre>
 *
 * @author prestonc, shivanim
 */
public class ConvertToAlgebricksExpressionsRule implements IAlgebraicRewriteRule {
    final Map<FunctionIdentifier, FunctionIdentifier> ALGEBRICKS_MAP = new HashMap<FunctionIdentifier, FunctionIdentifier>();
    final Map<FunctionIdentifier, FunctionIdentifier> ALGEBRICKS_BOOL_MAP = new HashMap<FunctionIdentifier, FunctionIdentifier>();
    final static String ALGEBRICKS_CONVERSION_ANNOTATION = "ConversionToAndFromAlgebricks";

    public ConvertToAlgebricksExpressionsRule() {
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.AND.getFunctionIdentifier(), AlgebricksBuiltinFunctions.AND);
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.OR.getFunctionIdentifier(), AlgebricksBuiltinFunctions.OR);
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.VALUE_EQ.getFunctionIdentifier(), AlgebricksBuiltinFunctions.EQ);
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.VALUE_GE.getFunctionIdentifier(), AlgebricksBuiltinFunctions.GE);
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.VALUE_GT.getFunctionIdentifier(), AlgebricksBuiltinFunctions.GT);
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.VALUE_LE.getFunctionIdentifier(), AlgebricksBuiltinFunctions.LE);
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.VALUE_LT.getFunctionIdentifier(), AlgebricksBuiltinFunctions.LT);
        ALGEBRICKS_BOOL_MAP.put(BuiltinOperators.VALUE_NE.getFunctionIdentifier(), AlgebricksBuiltinFunctions.NEQ);

        ALGEBRICKS_MAP.put(BuiltinFunctions.FN_EMPTY_1.getFunctionIdentifier(), AlgebricksBuiltinFunctions.IS_NULL);
        ALGEBRICKS_MAP.put(BuiltinFunctions.FN_NOT_1.getFunctionIdentifier(), AlgebricksBuiltinFunctions.NOT);
    }

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        boolean modified = false;
        List<Mutable<ILogicalExpression>> expressions = OperatorToolbox.getExpressions(opRef);
        for (Mutable<ILogicalExpression> expression : expressions) {
            if (processExpression(expression, context)) {
                modified = true;
            }
        }
        return modified;
    }

    private boolean processExpression(Mutable<ILogicalExpression> search, IOptimizationContext context) {
        return checkAllFunctionExpressions(search, context);
    }

    public boolean convertFunctionToAlgebricksExpression(Mutable<ILogicalExpression> searchM,
            AbstractFunctionCallExpression functionCall, IOptimizationContext context,
            Map<FunctionIdentifier, FunctionIdentifier> map) {

        if (map.containsKey(functionCall.getFunctionIdentifier())) {
            IExpressionAnnotation annotate = new ExpressionAnnotationNoCopyImpl();
            annotate.setObject(functionCall.getFunctionIdentifier());
            FunctionIdentifier algebricksFid = map.get(functionCall.getFunctionIdentifier());
            IFunctionInfo algebricksFunction = context.getMetadataProvider().lookupFunction(algebricksFid);
            functionCall.setFunctionInfo(algebricksFunction);
            functionCall.getAnnotations().put(ALGEBRICKS_CONVERSION_ANNOTATION, annotate);
            searchM.setValue(functionCall);
            return true;
        }
        return false;
    }

    public boolean checkAllFunctionExpressions(Mutable<ILogicalExpression> search, IOptimizationContext context) {
        boolean modified = false;
        ILogicalExpression le = search.getValue();

        if (le.getExpressionTag() == LogicalExpressionTag.FUNCTION_CALL) {
            AbstractFunctionCallExpression afce = (AbstractFunctionCallExpression) le;
            if (afce.getFunctionIdentifier().equals(BuiltinFunctions.FN_BOOLEAN_1.getFunctionIdentifier())) {
                ILogicalExpression argFirst = afce.getArguments().get(0).getValue();
                if (argFirst.getExpressionTag() == LogicalExpressionTag.FUNCTION_CALL) {
                    AbstractFunctionCallExpression functionCall = (AbstractFunctionCallExpression) argFirst;
                    if (convertFunctionToAlgebricksExpression(search, functionCall, context, ALGEBRICKS_BOOL_MAP)) {
                        modified = true;
                    }
                }
            } else if (ALGEBRICKS_MAP.containsKey(afce.getFunctionIdentifier())) {
                if (convertFunctionToAlgebricksExpression(search, afce, context, ALGEBRICKS_MAP)) {
                    modified = true;
                }
            } else {
            }
            for (Mutable<ILogicalExpression> argExp : afce.getArguments()) {
                if (checkAllFunctionExpressions(argExp, context)) {
                    modified = true;
                }
            }
        }
        return modified;
    }
}
