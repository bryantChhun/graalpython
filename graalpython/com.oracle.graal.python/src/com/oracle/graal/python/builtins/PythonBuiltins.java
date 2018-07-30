/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;

public abstract class PythonBuiltins {
    protected final Map<String, Object> builtinConstants = new HashMap<>();
    private final Map<String, BoundBuiltinCallable<?>> builtinFunctions = new HashMap<>();
    private final Map<PythonBuiltinClass, Map.Entry<Class<?>[], Boolean>> builtinClasses = new HashMap<>();

    protected abstract List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories();

    public void initialize(PythonCore core) {
        if (builtinFunctions.size() > 0) {
            return;
        }
        initializeEachFactoryWith((factory, builtin) -> {
            CoreFunctions annotation = getClass().getAnnotation(CoreFunctions.class);
            boolean declaresExplicitSelf = true;
            if (annotation.defineModule().length() > 0 && builtin.constructsClass().length == 0) {
                assert !builtin.isGetter();
                assert !builtin.isSetter();
                assert annotation.extendClasses().length == 0;
                // for module functions, explicit self is false by default
                declaresExplicitSelf = builtin.declaresExplicitSelf();
            }
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new BuiltinFunctionRootNode(core.getLanguage(), builtin, factory, declaresExplicitSelf));
            if (builtin.constructsClass().length > 0) {
                PBuiltinFunction newFunc = core.factory().createBuiltinFunction(__NEW__, null, createArity(builtin, declaresExplicitSelf), callTarget);
                PythonBuiltinClass builtinClass = createBuiltinClassFor(core, builtin);
                builtinClass.setAttributeUnsafe(__NEW__, newFunc);
                builtinClass.setAttribute(__DOC__, builtin.doc());
            } else {
                PBuiltinFunction function = core.factory().createBuiltinFunction(builtin.name(), null, createArity(builtin, declaresExplicitSelf), callTarget);
                function.setAttribute(__DOC__, builtin.doc());
                BoundBuiltinCallable<?> callable = function;
                if (builtin.isGetter() || builtin.isSetter()) {
                    PythonCallable get = builtin.isGetter() ? function : null;
                    PythonCallable set = builtin.isSetter() ? function : null;
                    callable = core.factory().createGetSetDescriptor(get, set, builtin.name(), null);
                }
                setBuiltinFunction(builtin.name(), callable);
            }
        });
    }

    public final void initializeClasses(PythonCore core) {
        assert builtinClasses.isEmpty();
        initializeEachFactoryWith((factory, builtin) -> {
            if (builtin.constructsClass().length > 0) {
                createBuiltinClassFor(core, builtin);
            }
        });
    }

    private PythonBuiltinClass createBuiltinClassFor(PythonCore core, Builtin builtin) {
        PythonBuiltinClass builtinClass = null;
        for (Class<?> klass : builtin.constructsClass()) {
            builtinClass = core.lookupType(klass);
            if (builtinClass != null) {
                break;
            }
        }
        if (builtinClass == null) {
            Class<?>[] bases = builtin.base();
            PythonBuiltinClass base = null;
            if (bases.length == 0) {
                base = core.getObjectClass();
            } else {
                assert bases.length == 1;
                // Search the "local scope" for builtin classes to inherit from
                outer: for (Entry<PythonBuiltinClass, Entry<Class<?>[], Boolean>> localClasses : builtinClasses.entrySet()) {
                    for (Class<?> o : localClasses.getValue().getKey()) {
                        if (o == bases[0]) {
                            base = localClasses.getKey();
                            break outer;
                        }
                    }
                }
                // Only take a globally known builtin class if we haven't found a local one
                if (base == null) {
                    base = core.lookupType(bases[0]);
                }
                assert base != null;
            }
            builtinClass = new PythonBuiltinClass(core.getTypeClass(), builtin.name(), base);
        }
        setBuiltinClass(builtinClass, builtin.constructsClass(), builtin.isPublic());
        return builtinClass;
    }

    private void initializeEachFactoryWith(BiConsumer<NodeFactory<? extends PythonBuiltinBaseNode>, Builtin> func) {
        List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> factories = getNodeFactories();
        assert factories != null : "No factories found. Override getFactories() to resolve this.";
        for (NodeFactory<? extends PythonBuiltinBaseNode> factory : factories) {
            Builtin builtin = factory.getNodeClass().getAnnotation(Builtin.class);
            func.accept(factory, builtin);
        }
    }

    private static Arity createArity(Builtin builtin, boolean declaresExplicitSelf) {
        int minNum = builtin.minNumOfArguments();
        int maxNum = Math.max(minNum, builtin.maxNumOfArguments());
        if (builtin.fixedNumOfArguments() > 0) {
            minNum = maxNum = builtin.fixedNumOfArguments();
        }
        if (!builtin.takesVariableArguments()) {
            maxNum += builtin.keywordArguments().length;
        }
        if (!declaresExplicitSelf) {
            // if we don't take the explicit self, we still need to accept it by arity
            minNum++;
            maxNum++;
        }
        return new Arity(builtin.name(), minNum, maxNum, builtin.keywordArguments().length > 0 || builtin.takesVariableKeywords(), builtin.takesVariableArguments(),
                        Arrays.asList(new String[0]), Arrays.asList(builtin.keywordArguments()));
    }

    private void setBuiltinFunction(String name, BoundBuiltinCallable<?> function) {
        builtinFunctions.put(name, function);
    }

    private void setBuiltinClass(PythonBuiltinClass builtinClass, Class<?>[] classes, boolean isPublic) {
        SimpleEntry<Class<?>[], Boolean> simpleEntry = new AbstractMap.SimpleEntry<>(classes, isPublic);
        builtinClasses.put(builtinClass, simpleEntry);
    }

    protected Map<String, BoundBuiltinCallable<?>> getBuiltinFunctions() {
        return builtinFunctions;
    }

    protected Map<PythonBuiltinClass, Entry<Class<?>[], Boolean>> getBuiltinClasses() {
        Map<PythonBuiltinClass, Entry<Class<?>[], Boolean>> tmp = builtinClasses;
        assert (tmp = Collections.unmodifiableMap(tmp)) != null;
        return tmp;
    }

    protected Map<String, Object> getBuiltinConstants() {
        return builtinConstants;
    }

}
