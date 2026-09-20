package com.jk.offermate;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // 不能硬编码 "com.jk.offermate"：debug 属于"本机组"，带 applicationIdSuffix
        // （见 app/build.gradle 里 localDevBuildTypes 的说明），实际包名随变体变化。
        assertEquals(BuildConfig.APPLICATION_ID, appContext.getPackageName());
    }
}