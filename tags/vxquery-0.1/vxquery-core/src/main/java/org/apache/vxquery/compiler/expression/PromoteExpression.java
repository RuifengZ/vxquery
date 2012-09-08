/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.vxquery.compiler.expression;

import java.util.Map;

import org.apache.vxquery.context.StaticContext;
import org.apache.vxquery.types.SequenceType;

public class PromoteExpression extends TypeExpression {
    public PromoteExpression(StaticContext ctx, Expression input, SequenceType type) {
        super(ctx, input, type);
    }

    @Override
    public <T> T accept(ExpressionVisitor<T> visitor) {
        return visitor.visitPromoteExpression(this);
    }

    @Override
    public ExprTag getTag() {
        return ExprTag.PROMOTE;
    }

    @Override
    public <T> T accept(MutableExpressionVisitor<T> visitor, ExpressionHandle handle) {
        return visitor.visitPromoteExpression(handle);
    }

    @Override
    public Expression copy(Map<Variable, Expression> substitution) {
        return new PromoteExpression(ctx, input.get().copy(substitution), type);
    }
}