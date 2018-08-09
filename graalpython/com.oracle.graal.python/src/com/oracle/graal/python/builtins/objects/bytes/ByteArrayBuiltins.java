/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PByteArray.class)
public class ByteArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ByteArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, takesVariableArguments = true, minNumOfArguments = 1, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PNone init(Object self, Object args, Object kwargs) {
            // TODO: tfel: throw an error if we get additional arguments and the __new__
            // method was the same as object.__new__
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELITEM__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.NormalizeIndexNode normalize = SequenceStorageNodes.NormalizeIndexNode.forArray();

        @Specialization(guards = "isByteStorage(primary)")
        protected PNone doBytes(PByteArray primary, long idx) {
            ByteSequenceStorage storage = (ByteSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isByteStorage(primary)")
        protected PNone doBytes(PByteArray primary, PInt idx) {
            ByteSequenceStorage storage = (ByteSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isIntStorage(primary)")
        protected PNone doInt(PByteArray primary, long idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization(guards = "isIntStorage(primary)")
        protected PNone doInt(PByteArray primary, PInt idx) {
            IntSequenceStorage storage = (IntSequenceStorage) primary.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doArray(PByteArray byteArray, long idx) {
            SequenceStorage storage = byteArray.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doArray(PByteArray byteArray, PInt idx) {
            SequenceStorage storage = byteArray.getSequenceStorage();
            storage.delItemInBound(normalize.execute(idx, storage.length()));
            return PNone.NONE;
        }

        @Specialization
        protected PNone doSlice(PByteArray self, PSlice slice) {
            self.delSlice(slice);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object idx) {
            if (!isValidIndexType(idx)) {
                throw raise(TypeError, "bytearray indices must be integers or slices, not %p", idx);
            }
            throw raise(TypeError, "descriptor '__delitem__' requires a 'bytearray' object but received a '%p'", idx);
        }

        protected boolean isValidIndexType(Object idx) {
            return PGuards.isInteger(idx) || idx instanceof PSlice;
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Child SequenceStorageNodes.EqNode eqNode;

        @Specialization
        public boolean eq(PByteArray self, PByteArray other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @Specialization
        public boolean eq(PByteArray self, PBytes other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PByteArray) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a 'bytearray' object but received a '%p'", self);
        }

        private SequenceStorageNodes.EqNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.EqNode.create());
            }
            return eqNode;
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PSequence self, PSequence other) {
            return self.lessThan(other);
        }
    }

    @Builtin(name = __ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object add(PByteArray self, PIBytesLike other,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            return factory().createByteArray(res);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytearray to %p", other);
        }
    }

    @Builtin(name = __MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBuiltinNode {

        @Specialization
        public Object mul(PByteArray self, int times,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            SequenceStorage res = repeatNode.execute(self.getSequenceStorage(), times);
            return factory().createByteArray(res);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object mul(Object self, Object other) {
            throw raise(TypeError, "can't multiply sequence by non-int of type '%p'", other);
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __STR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(PByteArray self) {
            return self.toString();
        }
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object repr(PByteArray self) {
            return self.toString();
        }
    }

    // bytearray.append(x)
    @Builtin(name = "append", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayAppendNode extends PythonBuiltinNode {

        @Specialization(guards = "isEmptyStorage(byteArray)")
        public PByteArray appendEmpty(PByteArray byteArray, Object arg) {
            byteArray.append(arg);
            return byteArray;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public PByteArray appendInt(PByteArray byteArray, int arg) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            try {
                store.appendInt(arg);
            } catch (SequenceStoreException e) {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
            return byteArray;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public PByteArray appendInt(PByteArray byteArray, byte arg) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            store.appendByte(arg);
            return byteArray;
        }
    }

    // bytearray.extend(L)
    @Builtin(name = "extend", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayExtendNode extends PythonBuiltinNode {

        @Specialization(guards = {"isPSequenceWithStorage(source)"}, rewriteOn = {SequenceStoreException.class})
        public PNone extendSequenceStore(PByteArray byteArray, Object source) throws SequenceStoreException {
            SequenceStorage target = byteArray.getSequenceStorage();
            target.extend(((PSequence) source).getSequenceStorage());
            return PNone.NONE;
        }

        @Specialization(guards = {"isPSequenceWithStorage(source)"})
        public PNone extendSequence(PByteArray byteArray, Object source) {
            SequenceStorage eSource = ((PSequence) source).getSequenceStorage();
            if (eSource.length() > 0) {
                SequenceStorage target = byteArray.getSequenceStorage();
                try {
                    target.extend(eSource);
                } catch (SequenceStoreException e) {
                    throw raise(ValueError, "byte must be in range(0, 256)");
                }
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isPSequenceWithStorage(source)")
        public PNone extend(PByteArray byteArray, Object source,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            Object workSource = byteArray != source ? source : factory().createByteArray(((PSequence) source).getSequenceStorage().copy());
            Object iterator = getIterator.executeWith(workSource);
            while (true) {
                Object value;
                try {
                    value = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return PNone.NONE;
                }

                try {
                    byteArray.append(value);
                } catch (SequenceStoreException e) {
                    throw raise(ValueError, "byte must be in range(0, 256)");
                }
            }
        }

        protected boolean isPSequenceWithStorage(Object source) {
            return (source instanceof PSequence && !(source instanceof PTuple || source instanceof PRange));
        }

    }

    // bytearray.copy()
    @Builtin(name = "copy", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayCopyNode extends PythonBuiltinNode {

        @Specialization
        public PByteArray copy(PByteArray byteArray) {
            return byteArray.copy();
        }
    }

    // bytearray.index(x)
    @Builtin(name = "index", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayIndexNode extends PythonBuiltinNode {
        @Specialization
        public int index(PByteArray byteArray, Object arg) {
            return byteArray.index(arg);
        }
    }

    // bytearray.count(x)
    @Builtin(name = "count", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayCountNode extends PythonBuiltinNode {

        @Specialization
        public int count(PByteArray byteArray, Object arg) {
            return byteArray.count(arg);
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayReverseNode extends PythonBuiltinNode {

        @Specialization
        public PNone reverse(PByteArray byteArray) {
            byteArray.reverse();
            return PNone.NONE;
        }
    }

    // bytearray.clear()
    @Builtin(name = "clear", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayClearNode extends PythonBuiltinNode {

        @Specialization
        public PNone clear(PByteArray byteArray) {
            byteArray.clear();
            return PNone.NONE;
        }
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayIterNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object iter(PByteArray byteArray,
                        @Cached("create()") GetIteratorNode getIterator) {
            return getIterator.executeWith(byteArray);
        }
    }

    @Builtin(name = "startswith", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class StartsWithNode extends PythonBuiltinNode {
        @Specialization
        boolean startswith(PByteArray self, PIBytesLike prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, 0, self.len()) == 0;
        }

        @Specialization
        boolean startswith(PByteArray self, PIBytesLike prefix, int start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, start, self.len()) == start;
        }

        @Specialization
        boolean startswith(PByteArray self, PIBytesLike prefix, int start, int end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, start, end) == start;
        }
    }

    @Builtin(name = "endswith", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class EndsWithNode extends PythonBuiltinNode {
        @Specialization
        boolean endswith(PByteArray self, PIBytesLike suffix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, suffix, self.len() - suffix.len(), self.len()) != -1;
        }
    }

    // bytearray.join(iterable)
    @Builtin(name = "join", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PBytes join(PByteArray bytes, Object iterable,
                        @Cached("create()") SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached("create()") BytesNodes.BytesJoinNode bytesJoinNode) {
            return factory().createBytes(bytesJoinNode.execute(toByteArrayNode.execute(bytes.getSequenceStorage()), iterable));
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object arg) {
            throw raise(TypeError, "can only join an iterable");
        }
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PByteArray self) {
            return self.len();
        }
    }

    @Builtin(name = SpecialMethodNames.__CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PSequence self, Object other) {
            return self.index(other) != -1;
        }
    }

    @Builtin(name = "find", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonBuiltinNode {
        @Child private BytesNodes.FindNode findNode;

        @Specialization
        int find(PByteArray self, Object sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, self.len());
        }

        @Specialization
        int find(PByteArray self, Object sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, self.len());
        }

        @Specialization
        int find(PByteArray self, Object sub, int start, int ending) {
            return getFindNode().execute(self, sub, start, ending);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object sub, Object start, Object ending) {
            throw raise(TypeError, "argument should be integer or bytes-like object, not '%p'", sub);
        }

        private BytesNodes.FindNode getFindNode() {
            if (findNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                findNode = insert(BytesNodes.FindNode.create());
            }
            return findNode;
        }
    }

    @Builtin(name = "translate", minNumOfArguments = 2, maxNumOfArguments = 3)
    @GenerateNodeFactory
    abstract static class TranslateNode extends PythonBuiltinNode {

        @Child private SequenceStorageNodes.GetItemNode getSelfItemNode;
        @Child private SequenceStorageNodes.GetItemNode getTableItemNode;
        @Child private SequenceStorageNodes.SetItemNode setItemNode;

        @Specialization
        PByteArray translate(PByteArray self, PBytes table, @SuppressWarnings("unused") PNone delete) {
            return translate(self.getSequenceStorage(), table.getSequenceStorage());
        }

        @Specialization
        PByteArray translate(PByteArray self, PByteArray table, @SuppressWarnings("unused") PNone delete) {
            return translate(self.getSequenceStorage(), table.getSequenceStorage());
        }

        private PByteArray translate(SequenceStorage selfStorage, SequenceStorage tableStorage) {
            if (tableStorage.length() != 256) {
                throw raise(ValueError, "translation table must be 256 characters long");
            }
            byte[] result = new byte[selfStorage.length()];
            for (int i = 0; i < selfStorage.length(); i++) {
                int b = getGetSelfItemNode().executeInt(selfStorage, i);
                int t = getGetTableItemNode().executeInt(tableStorage, b);
                assert t >= 0 && t < 256;
                result[i] = (byte) t;
            }
            return factory().createByteArray(result);
        }

        private SequenceStorageNodes.GetItemNode getGetSelfItemNode() {
            if (getSelfItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSelfItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getSelfItemNode;
        }

        private SequenceStorageNodes.GetItemNode getGetTableItemNode() {
            if (getTableItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTableItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getTableItemNode;
        }

    }

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isScalar(idx)")
        Object doScalar(PByteArray self, Object idx,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(self.getSequenceStorage(), idx);
        }

        @Specialization
        Object doSlice(PByteArray self, PSlice slice,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return factory().createByteArray(self.getPythonClass(), (ByteSequenceStorage) getSequenceItemNode.execute(self.getSequenceStorage(), slice));
        }

        protected static GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forBytearray());
        }

        protected boolean isScalar(Object obj) {
            return PGuards.isInteger(obj) || PGuards.isPInt(obj);
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Child private SequenceStorageNodes.NormalizeIndexNode normalize;

        @Specialization
        PNone doInt(PByteArray self, int idx, Object value) {
            self.setItemNormalized(ensureNormalize().execute(idx, self.len()), value);
            return PNone.NONE;
        }

        @Specialization
        PNone doSliceSequence(PByteArray self, PSlice slice, PSequence value) {
            self.setSlice(slice, value);
            return PNone.NONE;
        }

        @Specialization
        PNone doSliceMemoryview(PByteArray self, PSlice slice, PMemoryView value,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile) {
            Object bytesObj = callToBytesNode.executeObject(value);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                doSliceSequence(self, slice, (PBytes) bytesObj);
                return PNone.NONE;
            }
            throw raise(SystemError, "could not get bytes of memoryview");
        }

        @Specialization(guards = "isScalar(value)")
        @SuppressWarnings("unused")
        PNone doSliceScalar(PByteArray self, PSlice slice, Object value) {
            throw raise(TypeError, "can assign only bytes, buffers, or iterables of ints in range(0, 256)");
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public Object doLong(PByteArray primary, long idx, Object value) {
            return doInt(primary, PInt.intValueExact(idx), value);
        }

        @Specialization(replaces = "doLong")
        public Object doLongOvf(PByteArray primary, long idx, Object value) {
            try {
                return doInt(primary, PInt.intValueExact(idx), value);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        public Object doPInt(PByteArray primary, PInt idx, Object value) {
            return doInt(primary, idx.intValueExact(), value);
        }

        @Specialization(replaces = "doPInt")
        public Object doPIntOvf(PByteArray primary, PInt idx, Object value) {
            try {
                return doInt(primary, idx.intValueExact(), value);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object idx, Object value) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private SequenceStorageNodes.NormalizeIndexNode ensureNormalize() {
            if (normalize == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                normalize = insert(SequenceStorageNodes.NormalizeIndexNode.forArrayAssign());
            }
            return normalize;
        }

        protected boolean isScalar(Object value) {
            return !(value instanceof PSequence || value instanceof PMemoryView);
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization(guards = "isEmptyStorage(byteArray)")
        public boolean doEmpty(@SuppressWarnings("unused") PByteArray byteArray) {
            return false;
        }

        @Specialization(guards = "isIntStorage(byteArray)")
        public boolean doInt(PByteArray byteArray) {
            IntSequenceStorage store = (IntSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public boolean doByte(PByteArray byteArray) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization
        boolean doLen(PByteArray operand) {
            return operand.len() != 0;
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
