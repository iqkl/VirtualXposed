package com.lody.virtual.client.fixer;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.DropBoxManager;

import com.lody.virtual.client.core.PatchManager;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.hook.binders.HookDropBoxBinder;
import com.lody.virtual.client.hook.patchs.dropbox.DropBoxManagerPatch;
import com.lody.virtual.helper.utils.Reflect;
import com.lody.virtual.helper.utils.ReflectException;
import com.lody.virtual.helper.utils.VLog;

import java.lang.reflect.Method;

/**
 * @author Lody
 *
 */
public class ContextFixer {

	private static final String TAG = ContextFixer.class.getSimpleName();
	private static Class<?> CONTEXT_IMPL_CLASS = null;
	private static Method m_setOuterContext = null;

	static {
		try {
			CONTEXT_IMPL_CLASS = Class.forName("android.app.ContextImpl");
		} catch (ClassNotFoundException e) {
			// Ignore
		}
		try {
			m_setOuterContext = CONTEXT_IMPL_CLASS.getDeclaredMethod("setOuterContext", Context.class);
			if (!m_setOuterContext.isAccessible()) {
				m_setOuterContext.setAccessible(true);
			}
		} catch (Throwable e) {
			// Ignore
		}
	}

	/**
	 * Fuck AppOps
	 *
	 * @param plugin
	 *            插件Context
	 */
	public static void fixContext(Context plugin) {
		DropBoxManager dm = (DropBoxManager) plugin.getSystemService(Context.DROPBOX_SERVICE);
		HookDropBoxBinder boxBinder = PatchManager.getInstance().getHookObject(DropBoxManagerPatch.class);
		if (boxBinder != null) {
			try {
				Reflect.on(dm).set("mService", boxBinder.getProxyObject());
			} catch (ReflectException e) {
				e.printStackTrace();
			}
		}
		String pkgName = VirtualCore.getCore().getHostPkg();
		Context context = plugin;
		while (context instanceof ContextWrapper) {
			context = ((ContextWrapper) context).getBaseContext();
		}
		Reflect ref = Reflect.on(context);
		try {
			ref.set("mBasePackageName", pkgName);
		} catch (Throwable e) {
			VLog.w(TAG, "Unable to found field:mBasePackageName in ContextImpl, ignore.");
		}
    
    /**
     * By : qiang.sheng
     * TODO : fix摄像头不能调用的问题, 但不确定各版本之间的兼容性,先这么改着.
     * */
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      try {
        Object mBoundApplication = Reflect.on(VirtualCore.getCore().mainThread()).get("mBoundApplication");
        if (mBoundApplication != null) {
          ApplicationInfo appInfo = Reflect.on(mBoundApplication).get("appInfo");
          if (appInfo != null) {
            Reflect.on(appInfo).set("packageName", VirtualCore.getCore().getHostPkg());
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }


		if (Build.VERSION.SDK_INT >= 19) {
			try {
				ref.set("mOpPackageName", pkgName);
			} catch (Throwable e) {
				VLog.d(TAG, "Unable to found field:mOpPackageName in ContextImpl, ignore.");
			}
		}
		if (Build.VERSION.SDK_INT >= 18) {
			try {
				ContentResolver resolver = context.getContentResolver();
				Reflect.on(resolver).set("mPackageName", pkgName);
			} catch (Throwable e) {
				VLog.d(TAG, "Unable to found field:mPackageName in ContentProvider, ignore.");
			}
		}
	}

	public static void setOuterContext(Context contextImpl, Context outerContext) {
		try {
			m_setOuterContext.invoke(contextImpl, outerContext);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
