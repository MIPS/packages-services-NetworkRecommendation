/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.networkrecommendation;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.verify;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.net.RecommendationRequest;

import com.android.networkrecommendation.shadows.RecommendationRequestShadow;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest="packages/services/NetworkRecommendation/AndroidManifest.xml", sdk=23,
    shadows={RecommendationRequestShadow.class})
public class ExampleTest {
    @Mock private Context mMockContext;
    @Mock private JobScheduler mJobScheduler;
    @Mock private Intent mMockIntent;
    @Mock private RecommendationRequest request;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void buildRecommendation() {
      new RecommendationRequest.Builder().build();
    }

    @Test
    public void build() {
        new Example().buildRecommendationRequest();
    }

    @Test
    public void reflect() {
        new Example().reflectRecommendationRequest();
    }
}
