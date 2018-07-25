/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.datamodel;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class IsHashableNode extends PDataModelEmulationNode {
    protected PythonClass getBuiltinIntType() {
        return getCore().lookupType(PythonBuiltinClassType.PInt);
    }

    protected boolean isDouble(Object object) {
        return object instanceof Double || PGuards.isPFloat(object);
    }

    @Specialization(guards = "isString(object)")
    protected boolean isHashableString(@SuppressWarnings("unused") Object object) {
        return true;
    }

    @Specialization(guards = "isInteger(object)")
    protected boolean isHashableInt(@SuppressWarnings("unused") Object object) {
        return true;
    }

    @Specialization(guards = "isDouble(object)")
    protected boolean isHashableGeneric(@SuppressWarnings("unused") Object object) {
        return true;
    }

    @Specialization
    protected boolean isHashableGeneric(Object object,
                    @Cached("create(__HASH__)") LookupAndCallUnaryNode lookupHashAttributeNode,
                    @Cached("create()") BuiltinFunctions.IsInstanceNode isInstanceNode,
                    @Cached("getBuiltinIntType()") PythonClass IntType) {
        Object hashValue = lookupHashAttributeNode.executeObject(object);
        if (isInstanceNode.executeWith(hashValue, IntType)) {
            return true;
        }
        throw raise(PythonErrorType.TypeError, "__hash__ method should return an integer");
    }

    public static IsHashableNode create() {
        return IsHashableNodeGen.create();
    }
}
