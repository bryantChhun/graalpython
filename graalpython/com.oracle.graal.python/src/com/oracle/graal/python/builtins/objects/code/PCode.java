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
package com.oracle.graal.python.builtins.objects.code;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.truffle.api.nodes.RootNode;

public class PCode extends PythonBuiltinObject {
    private final PythonParseResult result;
    private final int argcount;
    private final int kwonlyargcount;
    private final int nlocals;
    private final int stacksize;
    private final int flags;
    private final String codestring;
    private final Object constants;
    private final Object names;
    private final Object varnames;
    private final String filename;
    private final String name;
    private final int firstlineno;
    private final Object lnotab;
    private final Object freevars;
    private final Object cellvars;

    public PCode(PythonClass cls, PythonParseResult result) {
        super(cls);
        this.result = result;
        this.argcount = -1;
        this.kwonlyargcount = -1;
        this.nlocals = -1;
        this.stacksize = -1;
        this.flags = -1;
        this.codestring = null;
        this.constants = null;
        this.names = null;
        this.varnames = null;
        this.filename = null;
        this.name = null;
        this.firstlineno = -1;
        this.lnotab = null;
        this.freevars = null;
        this.cellvars = null;
    }

    public PCode(PythonClass cls, int argcount, int kwonlyargcount, int nlocals, int stacksize,
                    int flags, String codestring, Object constants, Object names, Object varnames,
                    String filename, String name, int firstlineno, Object lnotab, Object freevars,
                    Object cellvars) {
        super(cls);
        this.result = null;
        this.argcount = argcount;
        this.kwonlyargcount = kwonlyargcount;
        this.nlocals = nlocals;
        this.stacksize = stacksize;
        this.flags = flags;
        this.codestring = codestring;
        this.constants = constants;
        this.names = names;
        this.varnames = varnames;
        this.filename = filename;
        this.name = name;
        this.firstlineno = firstlineno;
        this.lnotab = lnotab;
        this.freevars = freevars;
        this.cellvars = cellvars;
    }

    public RootNode getRootNode() {
        if (result != null) {
            return result.getRootNode();
        } else {
            return null;
        }
    }

    public Object getFreeVars() {
        return freevars;
    }

    public Object getCellVars() {
        return cellvars;
    }

    public Object getFilename() {
        return filename;
    }

    public int getFirstLineNo() {
        return firstlineno;
    }

    public String getName() {
        return name;
    }

    public int getArgcount() {
        return argcount;
    }

    public int getKwonlyargcount() {
        return kwonlyargcount;
    }

    public int getNlocals() {
        return nlocals;
    }

    public int getStacksize() {
        return stacksize;
    }

    public int getFlags() {
        return flags;
    }

    public String getCodestring() {
        return codestring;
    }

    public Object getConstants() {
        return constants;
    }

    public Object getNames() {
        return names;
    }

    public Object getVarnames() {
        return varnames;
    }

    public Object getLnotab() {
        return lnotab;
    }
}
