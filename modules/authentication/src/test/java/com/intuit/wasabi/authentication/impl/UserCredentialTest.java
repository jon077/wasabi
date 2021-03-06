/*******************************************************************************
 * Copyright 2016 Intuit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.intuit.wasabi.authentication.impl;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Testing the pojo UserCredential
 * Created on 2/4/16.
 */
public class UserCredentialTest {

    @Test
    public void nullInput(){
        UserCredential result = new UserCredential(null, null);
        assertThat(result.toBase64Encode(), is("bnVsbDpudWxs"));
    }

    @Test
    public void nonNullInput(){
        UserCredential result = new UserCredential("wasabi_admin", "admin_wasabi");
        assertThat(result.toBase64Encode(), is("d2FzYWJpX2FkbWluOmFkbWluX3dhc2FiaQ=="));
    }


}
