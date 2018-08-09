package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.CastToByteNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ConcatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.EqNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.NormalizeIndexNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.RepeatNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemScalarNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.SetItemSliceNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.StorageToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.VerifyNativeItemNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage.ElementType;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class SequenceStorageNodes {

    abstract static class SequenceStorageBaseNode extends PBaseNode {

        protected static boolean isByteStorage(NativeSequenceStorage store) {
            return store.getElementType() == ElementType.BYTE;
        }

        protected static boolean compatible(SequenceStorage left, NativeSequenceStorage right) {
            switch (right.getElementType()) {
                case BYTE:
                    return left instanceof ByteSequenceStorage;
                case INT:
                    return left instanceof IntSequenceStorage;
                case LONG:
                    return left instanceof LongSequenceStorage;
                case DOUBLE:
                    return left instanceof DoubleSequenceStorage;
                case OBJECT:
                    return left instanceof ObjectSequenceStorage || left instanceof TupleSequenceStorage || left instanceof ListSequenceStorage;
            }
            assert false : "should not reach";
            return false;
        }

        protected static boolean isNative(SequenceStorage store) {
            return store instanceof NativeSequenceStorage;
        }

    }

    public abstract static class GetItemNode extends PBaseNode {
        @Child private GetItemScalarNode getItemScalarNode;
        @Child private GetItemSliceNode getItemSliceNode;
        @Child private NormalizeIndexNode normalizeIndexNode;

        public GetItemNode(NormalizeIndexNode normalizeIndexNode) {
            this.normalizeIndexNode = normalizeIndexNode;
        }

        public abstract Object execute(SequenceStorage s, Object key);

        public abstract int executeInt(SequenceStorage s, int key);

        public abstract long executeLong(SequenceStorage s, long key);

        @Specialization
        protected Object doScalarInt(SequenceStorage storage, int idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()));
        }

        @Specialization
        protected Object doScalarLong(SequenceStorage storage, long idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()));
        }

        @Specialization
        protected Object doScalarPInt(SequenceStorage storage, PInt idx) {
            return getGetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()));
        }

        @Specialization
        protected Object doSlice(SequenceStorage storage, PSlice slice) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            return getGetItemSliceNode().execute(storage, info.start, info.stop, info.step, info.length);
        }

        private GetItemScalarNode getGetItemScalarNode() {
            if (getItemScalarNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemScalarNode = insert(GetItemScalarNode.create());
            }
            return getItemScalarNode;
        }

        private GetItemSliceNode getGetItemSliceNode() {
            if (getItemSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemSliceNode = insert(GetItemSliceNode.create());
            }
            return getItemSliceNode;
        }

        public static GetItemNode create(NormalizeIndexNode normalizeIndexNode) {
            return GetItemNodeGen.create(normalizeIndexNode);
        }

        public static GetItemNode create() {
            return GetItemNodeGen.create(NormalizeIndexNode.create());
        }
    }

    abstract static class GetItemScalarNode extends SequenceStorageBaseNode {

        @Child private Node readNode;
        @Child private VerifyNativeItemNode verifyNativeItemNode;

        @CompilationFinal private BranchProfile invalidTypeProfile;

        public abstract Object execute(SequenceStorage s, int idx);

        public static GetItemScalarNode create() {
            return GetItemScalarNodeGen.create();
        }

        public abstract byte executeByte(SequenceStorage s, int idx);

        public abstract int executeInt(SequenceStorage s, int idx);

        public abstract long executeLong(SequenceStorage s, int idx);

        public abstract double executeDouble(SequenceStorage s, int idx);

        @Specialization
        protected int doByte(ByteSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        @Specialization
        protected int doInt(IntSequenceStorage storage, int idx) {
            return storage.getIntItemNormalized(idx);
        }

        protected long doLong(LongSequenceStorage storage, int idx) {
            return storage.getLongItemNormalized(idx);
        }

        @Specialization
        protected double doDouble(DoubleSequenceStorage storage, int idx) {
            return storage.getDoubleItemNormalized(idx);
        }

        @Specialization
        protected Object doObject(ObjectSequenceStorage storage, int idx) {
            return storage.getItemNormalized(idx);
        }

        @Specialization(guards = "!isByteStorage(storage)")
        protected Object doNative(NativeSequenceStorage storage, int idx) {
            try {
                return verifyResult(storage, ForeignAccess.sendRead(getReadNode(), (TruffleObject) storage.getPtr(), idx));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Specialization(guards = "isByteStorage(storage)")
        protected int doNativeByte(NativeSequenceStorage storage, int idx) {
            Object result = doNative(storage, idx);
            return (byte) result & 0xFF;
        }

        private Object verifyResult(NativeSequenceStorage storage, Object item) {
            if (verifyNativeItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                verifyNativeItemNode = insert(VerifyNativeItemNode.create());
            }
            if (invalidTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invalidTypeProfile = BranchProfile.create();
            }
            if (!verifyNativeItemNode.execute(storage.getElementType(), item)) {
                invalidTypeProfile.enter();
                throw raise(SystemError, "Invalid item type %s returned from native sequence storage (expected: %s)", item, storage.getElementType());
            }
            return item;
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

    }

    @ImportStatic(ElementType.class)
    abstract static class GetItemSliceNode extends PBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract Object execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization(limit = "5", guards = {"storage.getClass() == cachedClass"})
        protected SequenceStorage doManagedStorage(BasicSequenceStorage storage, int start, int stop, int step, int length,
                        @Cached("storage.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            return cachedClass.cast(storage).getSliceInBound(start, stop, step, length);
        }

        @Specialization(guards = "storage.getElementType() == BYTE")
        protected NativeSequenceStorage doNativeByte(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (byte) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == INT")
        protected NativeSequenceStorage doNativeInt(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            int[] newArray = new int[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (int) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == LONG")
        protected NativeSequenceStorage doNativeLong(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            long[] newArray = new long[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (long) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == DOUBLE")
        protected NativeSequenceStorage doNativeDouble(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            double[] newArray = new double[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (double) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == OBJECT")
        protected NativeSequenceStorage doNativeObject(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        private Object readNativeElement(TruffleObject ptr, int idx) {
            try {
                return ForeignAccess.sendRead(getReadNode(), ptr, idx);
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

// private Node getExecuteNode() {
// if (executeNode == null) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// executeNode = insert(Message.createExecute(5).createNode());
// }
// return executeNode;
// }

        public static GetItemSliceNode create() {
            return GetItemSliceNodeGen.create();
        }
    }

    public abstract static class SetItemNode extends PBaseNode {
        @Child private SetItemScalarNode setItemScalarNode;
        @Child private SetItemSliceNode setItemSliceNode;
        @Child private NormalizeIndexNode normalizeIndexNode;

        public SetItemNode(NormalizeIndexNode normalizeIndexNode) {
            this.normalizeIndexNode = normalizeIndexNode;
        }

        public abstract void execute(SequenceStorage s, Object key, Object value);

        public abstract void executeInt(SequenceStorage s, int key, Object value);

        public abstract void executeLong(SequenceStorage s, long key, Object value);

        @Specialization
        protected void doScalarInt(SequenceStorage storage, int idx, Object value) {
            getSetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()), value);
        }

        @Specialization
        protected void doScalarLong(SequenceStorage storage, long idx, Object value) {
            getSetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()), value);
        }

        @Specialization
        protected void doScalarPInt(SequenceStorage storage, PInt idx, Object value) {
            getSetItemScalarNode().execute(storage, normalizeIndexNode.execute(idx, storage.length()), value);
        }

        @Specialization
        protected void doSlice(SequenceStorage storage, PSlice slice, PSequence value) {
            SliceInfo info = slice.computeActualIndices(storage.length());
            getSetItemSliceNode().execute(storage, info.start, info.stop, info.step, info.length);
        }

        private SetItemScalarNode getSetItemScalarNode() {
            if (setItemScalarNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemScalarNode = insert(SetItemScalarNode.create());
            }
            return setItemScalarNode;
        }

        private SetItemSliceNode getSetItemSliceNode() {
            if (setItemSliceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // TODO
                // setItemSliceNode = insert(SetItemSliceNode.create());
            }
            return setItemSliceNode;
        }

        public static SetItemNode create(NormalizeIndexNode normalizeIndexNode) {
            return SetItemNodeGen.create(normalizeIndexNode);
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create(NormalizeIndexNode.create());
        }

    }

    abstract static class SetItemScalarNode extends SequenceStorageBaseNode {

        @Child private Node writeNode;
        @Child private VerifyNativeItemNode verifyNativeItemNode;
        @Child private CastToByteNode castToByteNode;

        @CompilationFinal private BranchProfile invalidTypeProfile;

        public abstract void execute(SequenceStorage s, int idx, Object value);

        @Specialization
        protected void doByte(ByteSequenceStorage storage, int idx, Object value) {
            storage.setByteItemNormalized(idx, getCastToByteNode().execute(value));
        }

        @Specialization
        protected void doInt(IntSequenceStorage storage, int idx, int value) {
            storage.setIntItemNormalized(idx, value);
        }

        protected void doLong(LongSequenceStorage storage, int idx, long value) {
            storage.setLongItemNormalized(idx, value);
        }

        @Specialization
        protected void doDouble(DoubleSequenceStorage storage, int idx, double value) {
            storage.setDoubleItemNormalized(idx, value);
        }

        @Specialization
        protected void doObject(ObjectSequenceStorage storage, int idx, Object value) {
            storage.setItemNormalized(idx, value);
        }

        @Specialization(guards = "isByteStorage(storage)")
        protected void doNativeByte(NativeSequenceStorage storage, int idx, Object value) {
            try {
                ForeignAccess.sendWrite(getWriteNode(), (TruffleObject) storage.getPtr(), idx, getCastToByteNode().execute(value));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        @Specialization
        protected void doNative(NativeSequenceStorage storage, int idx, Object value) {
            try {
                ForeignAccess.sendWrite(getWriteNode(), (TruffleObject) storage.getPtr(), idx, verifyValue(storage, value));
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        private Node getWriteNode() {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(Message.WRITE.createNode());
            }
            return writeNode;
        }

        private CastToByteNode getCastToByteNode() {
            if (castToByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToByteNode = insert(CastToByteNode.create());
            }
            return castToByteNode;
        }

        private Object verifyValue(NativeSequenceStorage storage, Object item) {
            if (verifyNativeItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                verifyNativeItemNode = insert(VerifyNativeItemNode.create());
            }
            if (invalidTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invalidTypeProfile = BranchProfile.create();
            }
            if (!verifyNativeItemNode.execute(storage.getElementType(), item)) {
                invalidTypeProfile.enter();
                throw raise(TypeError, "%s is required, was %p", storage.getElementType(), item);
            }
            return item;
        }

        public static SetItemScalarNode create() {
            return SetItemScalarNodeGen.create();
        }
    }

    @ImportStatic(ElementType.class)
    public abstract static class SetItemSliceNode extends PBaseNode {

        @Child private Node readNode;
        @Child private Node executeNode;

        public abstract Object execute(SequenceStorage s, int start, int stop, int step, int length);

        @Specialization(limit = "5", guards = {"storage.getClass() == cachedClass"})
        protected SequenceStorage doManagedStorage(BasicSequenceStorage storage, int start, int stop, int step, int length,
                        @Cached("storage.getClass()") Class<? extends BasicSequenceStorage> cachedClass) {
            return cachedClass.cast(storage).getSliceInBound(start, stop, step, length);
        }

        @Specialization(guards = "storage.getElementType() == BYTE")
        protected NativeSequenceStorage doNativeByte(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            byte[] newArray = new byte[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (byte) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == INT")
        protected NativeSequenceStorage doNativeInt(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            int[] newArray = new int[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (int) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == LONG")
        protected NativeSequenceStorage doNativeLong(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            long[] newArray = new long[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (long) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == DOUBLE")
        protected NativeSequenceStorage doNativeDouble(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            double[] newArray = new double[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = (double) readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        @Specialization(guards = "storage.getElementType() == OBJECT")
        protected NativeSequenceStorage doNativeObject(NativeSequenceStorage storage, int start, @SuppressWarnings("unused") int stop, int step, int length,
                        @Cached("create()") StorageToNativeNode storageToNativeNode) {
            Object[] newArray = new Object[length];
            for (int i = start, j = 0; j < length; i += step, j++) {
                newArray[j] = readNativeElement((TruffleObject) storage.getPtr(), i);
            }
            return storageToNativeNode.execute(newArray);
        }

        private Object readNativeElement(TruffleObject ptr, int idx) {
            try {
                return ForeignAccess.sendRead(getReadNode(), ptr, idx);
            } catch (InteropException e) {
                throw e.raise();
            }
        }

        private Node getReadNode() {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            return readNode;
        }

        public static SetItemSliceNode create() {
            return SetItemSliceNodeGen.create();
        }
    }

    abstract static class VerifyNativeItemNode extends PBaseNode {

        public abstract boolean execute(ElementType expectedType, Object item);

        @Specialization(guards = "elementType == cachedElementType", limit = "1")
        boolean doCached(@SuppressWarnings("unused") ElementType elementType, Object item,
                        @Cached("elementType") ElementType cachedElementType) {
            return doGeneric(cachedElementType, item);
        }

        @Specialization(replaces = "doCached")
        boolean doGeneric(ElementType expectedType, Object item) {
            switch (expectedType) {
                case BYTE:
                    return item instanceof Byte;
                case INT:
                    return item instanceof Integer;
                case LONG:
                    return item instanceof Long;
                case DOUBLE:
                    return item instanceof Double;
                case OBJECT:
                    return !(item instanceof Byte || item instanceof Integer || item instanceof Long || item instanceof Double);
            }
            return false;
        }

        public static VerifyNativeItemNode create() {
            return VerifyNativeItemNodeGen.create();
        }

    }

    @ImportStatic(NativeCAPISymbols.class)
    public abstract static class StorageToNativeNode extends PBaseNode {
        @Child private Node executeNode;

        public abstract NativeSequenceStorage execute(Object obj);

        @Specialization
        NativeSequenceStorage doByte(byte[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.BYTE);
        }

        @Specialization
        NativeSequenceStorage doInt(int[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.INT);
        }

        @Specialization
        NativeSequenceStorage doLong(long[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.LONG);
        }

        @Specialization
        NativeSequenceStorage doDouble(double[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.DOUBLE);
        }

        @Specialization
        NativeSequenceStorage doObject(Object[] arr,
                        @Cached("create(FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE)") PCallBinaryCapiFunction callNode) {
            return new NativeSequenceStorage(callNode.execute(getContext().getEnv().asGuestValue(arr), arr.length), arr.length, arr.length, ElementType.OBJECT);
        }

        public static StorageToNativeNode create() {
            return StorageToNativeNodeGen.create();
        }
    }

    public static class PCallBinaryCapiFunction extends PBaseNode {

        @Child private Node callNode;

        private final String name;
        private final BranchProfile profile = BranchProfile.create();

        @CompilationFinal TruffleObject receiver;

        public PCallBinaryCapiFunction(String name) {
            this.name = name;
        }

        public Object execute(Object arg0, Object arg1) {
            try {
                return ForeignAccess.sendExecute(getCallNode(), getFunction(), arg0, arg1);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                profile.enter();
                throw e.raise();
            }
        }

        private Node getCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(Message.createExecute(2).createNode());
            }
            return callNode;
        }

        private TruffleObject getFunction() {
            if (receiver == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                receiver = (TruffleObject) getContext().getEnv().importSymbol(name);
            }
            return receiver;
        }

        public static PCallBinaryCapiFunction create(String name) {
            return new PCallBinaryCapiFunction(name);
        }
    }

    public abstract static class CastToByteNode extends PBaseNode {

        public abstract byte execute(Object val);

        @Specialization
        protected byte doByte(byte value) {
            return value;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doInt(int value) {
            return PInt.byteValueExact(value);
        }

        @Specialization(replaces = "doInt")
        protected byte doIntOvf(int value) {
            try {
                return PInt.byteValueExact(value);
            } catch (ArithmeticException e) {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doLong(long value) {
            return PInt.byteValueExact(value);
        }

        @Specialization(replaces = "doLong")
        protected byte doLongOvf(long value) {
            try {
                return PInt.byteValueExact(value);
            } catch (ArithmeticException e) {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        protected byte doPInt(PInt value) {
            return value.byteValueExact();
        }

        @Specialization(replaces = "doPInt")
        protected byte doPIntOvf(PInt value) {
            try {
                return value.byteValueExact();
            } catch (ArithmeticException e) {
                throw raise(ValueError, "byte must be in range(0, 256)");
            }
        }

        @Specialization
        protected byte doBoolean(boolean value) {
            return value ? (byte) 1 : (byte) 0;
        }

        @Fallback
        protected byte doGeneric(@SuppressWarnings("unused") Object val) {
            throw raise(TypeError, "an integer is required");
        }

        public static CastToByteNode create() {
            return CastToByteNodeGen.create();
        }

    }

    public abstract static class EqNode extends SequenceStorageBaseNode {
        @Child private GetItemNode getItemNode;
        @Child private GetItemNode getRightItemNode;
        @Child private BinaryComparisonNode equalsNode;

        public abstract boolean execute(SequenceStorage left, SequenceStorage right);

        @Specialization(guards = {"isEmpty(left)", "isEmpty(right)"})
        boolean doEmpty(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            return true;
        }

        @Specialization(guards = {"left.getClass() == right.getClass()", "!isNative(left)"})
        boolean doManagedManagedSameType(SequenceStorage left, SequenceStorage right) {
            assert !isNative(right);
            return left.equals(right);
        }

        @Specialization(guards = "left.getElementType() == right.getElementType()")
        boolean doNativeNativeSameType(NativeSequenceStorage left, NativeSequenceStorage right) {
            // TODO profile or guard !
            if (left.length() != right.length()) {
                return false;
            }
            for (int i = 0; i < left.length(); i++) {
                // use the same 'getItemNode'
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetItemNode().execute(right, i);
                if (!getEqualsNode().executeBool(leftItem, rightItem)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = {"!isNative(left)", "compatible(left, right)"})
        boolean doManagedNative(SequenceStorage left, NativeSequenceStorage right) {
            // TODO profile or guard !
            if (left.length() != right.length()) {
                return false;
            }
            for (int i = 0; i < left.length(); i++) {
                Object leftItem = getGetItemNode().execute(left, i);
                Object rightItem = getGetRightItemNode().execute(right, i);
                if (!getEqualsNode().executeBool(leftItem, rightItem)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = {"!isNative(right)", "compatible(right, left)"})
        boolean doNatveManaged(NativeSequenceStorage left, SequenceStorage right) {
            return doManagedNative(right, left);
        }

        @Fallback
        boolean doFallback(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            return false;
        }

        protected boolean isEmpty(SequenceStorage left) {
            // TODO use a node
            return left.length() == 0;
        }

        private GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemNode.create(NormalizeIndexNode.create()));
            }
            return getItemNode;
        }

        private GetItemNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemNode.create(NormalizeIndexNode.create()));
            }
            return getRightItemNode;
        }

        private BinaryComparisonNode getEqualsNode() {
            if (equalsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalsNode = insert(BinaryComparisonNode.create(__EQ__, __EQ__, "=="));
            }
            return equalsNode;
        }

        public static EqNode create() {
            return EqNodeGen.create();
        }

    }

    public abstract static class NormalizeIndexNode extends PBaseNode {
        public static final String INDEX_OUT_OF_BOUNDS = "index out of range";
        public static final String RANGE_OUT_OF_BOUNDS = "range index out of range";
        public static final String TUPLE_OUT_OF_BOUNDS = "tuple index out of range";
        public static final String LIST_OUT_OF_BOUNDS = "list index out of range";
        public static final String LIST_ASSIGN_OUT_OF_BOUNDS = "list assignment index out of range";
        public static final String ARRAY_OUT_OF_BOUNDS = "array index out of range";
        public static final String ARRAY_ASSIGN_OUT_OF_BOUNDS = "array assignment index out of range";
        public static final String BYTEARRAY_OUT_OF_BOUNDS = "bytearray index out of range";

        private final String errorMessage;
        private final boolean boundsCheck;
        private final ConditionProfile negativeIndexProfile = ConditionProfile.createBinaryProfile();

        @CompilationFinal private ConditionProfile outOfBoundsProfile;

        public NormalizeIndexNode(String errorMessage, boolean boundsCheck) {
            this.errorMessage = errorMessage;
            this.boundsCheck = boundsCheck;
        }

        public abstract int execute(Object index, int length);

        @Specialization
        int doInt(int index, int length) {
            int idx = index;
            if (negativeIndexProfile.profile(idx < 0)) {
                idx += length;
            }
            doBoundsCheck(idx, length);
            return idx;
        }

        private void doBoundsCheck(int idx, int length) {
            if (boundsCheck) {
                if (outOfBoundsProfile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    outOfBoundsProfile = ConditionProfile.createBinaryProfile();
                }
                if (outOfBoundsProfile.profile(idx < 0 || idx >= length)) {
                    throw raise(IndexError, errorMessage);
                }
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doLong(long index, int length) {
            int idx = PInt.intValueExact(index);
            return doInt(idx, length);
        }

        @Specialization(replaces = "doLong")
        int doLongOvf(long index, int length) {
            try {
                return doLong(index, length);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doPInt(PInt index, int length) {
            int idx = index.intValueExact();
            return doInt(idx, length);
        }

        @Specialization(replaces = "doPInt")
        int doPIntOvf(PInt index, int length) {
            try {
                return doPInt(index, length);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        public static NormalizeIndexNode create() {
            return create(INDEX_OUT_OF_BOUNDS, true);
        }

        public static NormalizeIndexNode create(String errorMessage) {
            return NormalizeIndexNodeGen.create(errorMessage, true);
        }

        public static NormalizeIndexNode create(boolean boundsCheck) {
            return NormalizeIndexNodeGen.create(INDEX_OUT_OF_BOUNDS, boundsCheck);
        }

        public static NormalizeIndexNode create(String errorMessage, boolean boundsCheck) {
            return NormalizeIndexNodeGen.create(errorMessage, boundsCheck);
        }

        public static NormalizeIndexNode forList() {
            return create(LIST_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forListAssign() {
            return create(LIST_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTuple() {
            return create(TUPLE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArray() {
            return create(ARRAY_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArrayAssign() {
            return create(ARRAY_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forRange() {
            return create(RANGE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forBytearray() {
            return create(BYTEARRAY_OUT_OF_BOUNDS);
        }
    }

    public abstract static class ToByteArrayNode extends SequenceStorageBaseNode {

        @Child private GetItemScalarNode getItemNode;

        private final boolean exact;

        public ToByteArrayNode(boolean exact) {
            this.exact = exact;
        }

        public abstract byte[] execute(SequenceStorage s);

        @Specialization
        byte[] doByteSequenceStorage(ByteSequenceStorage s) {
            byte[] barr = s.getInternalByteArray();
            if (exact) {
                return exactCopy(barr, s.length());
            }
            return barr;

        }

        @Specialization(guards = "isByteStorage(s)")
        byte[] doNativeByte(NativeSequenceStorage s) {
            byte[] barr = new byte[s.length()];
            for (int i = 0; i < barr.length; i++) {
                int elem = getGetItemNode().executeInt(s, i);
                assert elem >= 0 && elem < 256;
                barr[i] = (byte) elem;
            }
            return barr;
        }

        @Fallback
        byte[] doFallback(@SuppressWarnings("unused") SequenceStorage s) {
            throw raise(TypeError, "expected a bytes-like object");
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] exactCopy(byte[] barr, int len) {
            return Arrays.copyOf(barr, len);
        }

        protected GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        public static ToByteArrayNode create() {
            return ToByteArrayNodeGen.create(true);
        }

        public static ToByteArrayNode create(boolean exact) {
            return ToByteArrayNodeGen.create(exact);
        }
    }

    public abstract static class ConcatNode extends SequenceStorageBaseNode {
        @Child private SetItemScalarNode setItemNode;
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;

        public abstract SequenceStorage execute(SequenceStorage left, SequenceStorage right);

        @Specialization(guards = {"left.getClass() == right.getClass()", "!isNative(left)", "cachedClass == left.getClass()"})
        SequenceStorage doManagedManagedSameType(SequenceStorage left, SequenceStorage right,
                        @Cached("left.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage leftProfiled = cachedClass.cast(left);
            SequenceStorage rightProfiled = cachedClass.cast(right);
            Object arr1 = leftProfiled.getInternalArrayObject();
            int len1 = leftProfiled.length();
            Object arr2 = rightProfiled.getInternalArrayObject();
            int len2 = rightProfiled.length();
            SequenceStorage dest = leftProfiled.createEmpty(len1 + len2);
            concat(dest.getInternalArrayObject(), arr1, len1, arr2, len2);
            dest.setNewLength(len1 + len2);
            return dest;
        }

        @Specialization(guards = {"!isNative(left)", "compatible(left, right)"})
        SequenceStorage doManagedNative(SequenceStorage left, NativeSequenceStorage right) {
            int len1 = left.length();
            SequenceStorage dest = left.createEmpty(len1 + right.length());
            for (int i = 0; i < len1; i++) {
                getSetItemNode().execute(dest, i, getGetItemNode().execute(left, i));
            }
            for (int i = 0; i < right.length(); i++) {
                getSetItemNode().execute(dest, i + len1, getGetRightItemNode().execute(right, i));
            }
            return dest;
        }

        @Specialization(guards = {"!isNative(right)", "compatible(right, left)"})
        SequenceStorage doNatveManaged(NativeSequenceStorage left, SequenceStorage right) {
            return doManagedNative(right, left);
        }

        @Specialization
        SequenceStorage doGeneric(@SuppressWarnings("unused") SequenceStorage left, @SuppressWarnings("unused") SequenceStorage right) {
            // TODO complete
            throw raise(TypeError, "cannot concatenate sequences");
        }

        private SetItemScalarNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemScalarNode.create());
            }
            return setItemNode;
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        private GetItemScalarNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(GetItemScalarNode.create());
            }
            return getRightItemNode;
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static void concat(Object dest, Object arr1, int len1, Object arr2, int len2) {
            System.arraycopy(arr1, 0, dest, 0, len1);
            System.arraycopy(arr2, 0, dest, len1, len2);
        }

        public static ConcatNode create() {
            return ConcatNodeGen.create();
        }
    }

    public abstract static class RepeatNode extends SequenceStorageBaseNode {
        @Child private SetItemScalarNode setItemNode;
        @Child private GetItemScalarNode getItemNode;
        @Child private GetItemScalarNode getRightItemNode;

        public abstract SequenceStorage execute(SequenceStorage left, int times);

        @Specialization(limit = "2", guards = {"!isNative(s)", "s.getClass() == cachedClass"})
        SequenceStorage doManaged(SequenceStorage s, int times,
                        @Cached("s.getClass()") Class<? extends SequenceStorage> cachedClass) {
            SequenceStorage profiled = cachedClass.cast(s);
            Object arr1 = profiled.getInternalArrayObject();
            int len = profiled.length();
            SequenceStorage repeated = profiled.createEmpty(len * times);
            Object destArr = repeated.getInternalArrayObject();
            repeat(destArr, arr1, len, times);
            repeated.setNewLength(len * times);
            return repeated;
        }

        @Specialization(replaces = "doManaged")
        SequenceStorage doGeneric(SequenceStorage s, int times) {
            int len = s.length();

            // TODO avoid temporary array
            Object[] values = new Object[len];
            for (int i = 0; i < len; i++) {
                values[i] = getGetItemNode().execute(s, i);
            }

            ObjectSequenceStorage repeated = new ObjectSequenceStorage(len * times);
            Object destArr = repeated.getInternalArrayObject();
            repeat(destArr, values, len, times);
            return repeated;
        }

        private GetItemScalarNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemScalarNode.create());
            }
            return getItemNode;
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static void repeat(Object dest, Object src, int len, int times) {
            for (int i = 0; i < times; i++) {
                System.arraycopy(src, 0, dest, i * len, len);
            }
        }

        public static RepeatNode create() {
            return RepeatNodeGen.create();
        }
    }

}
