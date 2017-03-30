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
package com.android.networkrecommendation.shadows;

import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.Context;
import com.android.networkrecommendation.util.NotificationChannelUtil;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * A temporary shadow which is only useful until NotificationChannel and NotificationChannelGroup
 * classes is available to Robolectric tests. TODO(b/35959851): remove this class.
 */
@Implements(NotificationChannelUtil.class)
public class ShadowNotificationChannelUtil {

  @Implementation
  public static void configureNotificationChannels(
      NotificationManager notificationManager, Context context) {
    // Do nothing
  }

  @Implementation
  public static Builder setChannel(Builder notificationBuilder, String channelId) {
    return notificationBuilder;
  }
}
