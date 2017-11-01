/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.contacts.compat;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class CompatUtilsTest extends AndroidTestCase {

    public void testIsClassAvailable_NullClassName() {
        assertFalse(CompatUtils.isClassAvailable(null));
    }

    public void testIsClassAvailable_EmptyClassName() {
        assertFalse(CompatUtils.isClassAvailable(""));
    }

    public void testIsClassAvailable_NonexistentClass() {
        assertFalse(CompatUtils.isClassAvailable("com.android.contacts.common.NonexistentClass"));
    }

    public void testIsClassAvailable() {
        assertTrue(CompatUtils.isClassAvailable(BaseClass.class.getName()));
    }

    public void testIsMethodAvailable_NullClassName() {
        assertFalse(CompatUtils.isMethodAvailable(null, "methodName"));
    }

    public void testIsMethodAvailable_EmptyClassName() {
        assertFalse(CompatUtils.isMethodAvailable("", "methodName"));
    }

    public void testIsMethodAvailable_NullMethodName() {
        assertFalse(CompatUtils.isMethodAvailable("className", null));
    }

    public void testIsMethodAvailable_EmptyMethodName() {
        assertFalse(CompatUtils.isMethodAvailable("className", ""));
    }

    public void testIsMethodAvailable_NonexistentClass() {
        assertFalse(CompatUtils.isMethodAvailable("com.android.contacts.common.NonexistentClass",
                ""));
    }

    public void testIsMethodAvailable_NonexistentMethod() {
        assertFalse(CompatUtils.isMethodAvailable(BaseClass.class.getName(), "derivedMethod"));
    }

    public void testIsMethodAvailable() {
        assertTrue(CompatUtils.isMethodAvailable(BaseClass.class.getName(), "baseMethod"));
    }

    public void testIsMethodAvailable_InheritedMethod() {
        assertTrue(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "baseMethod"));
    }

    public void testIsMethodAvailable_OverloadedMethod() {
        assertTrue(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "overloadedMethod"));
        assertTrue(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "overloadedMethod",
                Integer.TYPE));
    }

    public void testIsMethodAvailable_NonexistentOverload() {
        assertFalse(CompatUtils.isMethodAvailable(DerivedClass.class.getName(), "overloadedMethod",
                Boolean.TYPE));
    }

    public void testInvokeMethod_NullMethodName() {
        assertNull(CompatUtils.invokeMethod(new BaseClass(), null, null, null));
    }

    public void testInvokeMethod_EmptyMethodName() {
        assertNull(CompatUtils.invokeMethod(new BaseClass(), "", null, null));
    }

    public void testInvokeMethod_NullClassInstance() {
        assertNull(CompatUtils.invokeMethod(null, "", null, null));
    }

    public void testInvokeMethod_NonexistentMethod() {
        assertNull(CompatUtils.invokeMethod(new BaseClass(), "derivedMethod", null, null));
    }

    public void testInvokeMethod_MethodWithNoParameters() {
        assertEquals(1, CompatUtils.invokeMethod(new DerivedClass(), "overloadedMethod", null, null));
    }

    public void testInvokeMethod_MethodWithNoParameters_WithParameters() {
        assertNull(CompatUtils.invokeMethod(new DerivedClass(), "derivedMethod",
                new Class<?>[] {Integer.TYPE}, new Object[] {1}));
    }

    public void testInvokeMethod_MethodWithParameters_WithEmptyParameterList() {
        assertNull(CompatUtils.invokeMethod(new DerivedClass(), "overloadedMethod",
                new Class<?>[] {Integer.TYPE}, new Object[] {}));
    }

    public void testInvokeMethod_InvokeSimpleMethod() {
        assertEquals(2, CompatUtils.invokeMethod(new DerivedClass(), "overloadedMethod",
                new Class<?>[] {Integer.TYPE}, new Object[] {2}));
    }

    private class BaseClass {
        public void baseMethod() {}
    }

    private class DerivedClass extends BaseClass {
        public int derivedMethod() {
            // This method needs to return something to differentiate a successful invocation from
            // an unsuccessful one.
            return 0;
        }

        public int overloadedMethod() {
            return 1;
        }

        public int overloadedMethod(int i) {
            return i;
        }
    }
}
