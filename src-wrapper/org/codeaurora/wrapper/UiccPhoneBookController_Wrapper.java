/*
 * Copyright (C) 2016-2017, The Linux Foundation. All Rights Reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above
         copyright notice, this list of conditions and the following
         disclaimer in the documentation and/or other materials provided
         with the distribution.
       * Neither the name of The Linux Foundation nor the names of its
         contributors may be used to endorse or promote products derived
         from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package org.codeaurora.wrapper;

import android.os.ServiceManager;
import com.android.internal.telephony.IIccPhoneBook;

public class UiccPhoneBookController_Wrapper {

    /*      capacity[0]  is the max count of ADN
            capacity[1]  is the used count of ADN
            capacity[2]  is the max count of EMAIL
            capacity[3]  is the used count of EMAIL
            capacity[4]  is the max count of ANR
            capacity[5]  is the used count of ANR
            capacity[6]  is the max length of name
            capacity[7]  is the max length of number
            capacity[8]  is the max length of email
            capacity[9]  is the max length of anr
    */
    public static int[] getAdnRecordsCapacityForSubscriber(int subId) {
        int defaultCapacity[] = { 0, 0, 0, 0, 0, 0, 14, 40, 40, 40 };
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                int[] capacity = iccIpb
                        .getAdnRecordsCapacityForSubscriber(subId);
                if (capacity != null)
                    return capacity;
            }
        } catch (Exception ex) {
        }
        return defaultCapacity;
    }
}
