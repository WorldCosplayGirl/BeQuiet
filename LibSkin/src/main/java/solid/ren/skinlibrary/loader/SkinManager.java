package solid.ren.skinlibrary.loader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import com.thin.downloadmanager.DefaultRetryPolicy;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListenerV1;
import com.thin.downloadmanager.ThinDownloadManager;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import solid.ren.skinlibrary.config.SkinConfig;
import solid.ren.skinlibrary.listener.ILoaderListener;
import solid.ren.skinlibrary.listener.ISkinLoader;
import solid.ren.skinlibrary.listener.ISkinUpdate;
import solid.ren.skinlibrary.utils.SkinFileUtils;
import solid.ren.skinlibrary.utils.SkinL;
import solid.ren.skinlibrary.utils.TypefaceUtils;

/**
 * Created by _SOLID
 * Date:2016/4/13
 * Time:21:07
 */
public class SkinManager implements ISkinLoader {

  private static volatile SkinManager mInstance;
  private List<ISkinUpdate> mSkinObservers;
  private Context context;
  private Resources mResources;
  // 当前的皮肤是否是默认的
  private boolean isDefaultSkin = false;
  // 皮肤的包名
  private String skinPackageName;
  // 皮肤路径
  private String skinPath;

  private SkinManager() {
  }

  public static SkinManager getInstance() {
    if (mInstance == null) {
      synchronized (SkinManager.class) {
        if (mInstance == null) {
          mInstance = new SkinManager();
        }
      }
    }
    return mInstance;
  }

  public void init(Context ctx) {
    context = ctx.getApplicationContext();
    TypefaceUtils.CURRENT_TYPEFACE = TypefaceUtils.getTypeface(context);
  }

  public Context getContext() {
    return context;
  }

  public int getColorPrimaryDark() {
    if (mResources != null) {
      int identify = mResources.getIdentifier("colorPrimaryDark", "color", skinPackageName);
      if (!(identify <= 0)) return mResources.getColor(identify);
    }
    return -1;
  }

  /**
   * 判断当前使用的皮肤是否来自外部
   *
   * @return
   */
  public boolean isExternalSkin() {
    return !isDefaultSkin && mResources != null;
  }

  /**
   * 得到当前的皮肤路径
   *
   * @return
   */
  public String getSkinPath() {
    return skinPath;
  }

  /**
   * 得到当前皮肤的包名
   *
   * @return
   */
  public String getSkinPackageName() {
    return skinPackageName;
  }

  public Resources getResources() {
    return mResources;
  }

  /**
   * 恢复到默认主题
   */
  public void restoreDefaultTheme() {
    SkinConfig.saveSkinPath(context, SkinConfig.DEFAULT_SKIN);
    isDefaultSkin = true;
    mResources = context.getResources();
    skinPackageName = context.getPackageName();
    notifySkinUpdate();
  }

  @Override public void attach(ISkinUpdate observer) {
    if (mSkinObservers == null) {
      mSkinObservers = new ArrayList<>();
    }
    if (!mSkinObservers.contains(observer)) {
      mSkinObservers.add(observer);
    }
  }

  @Override public void detach(ISkinUpdate observer) {
    if (mSkinObservers == null) return;
    if (mSkinObservers.contains(observer)) {
      mSkinObservers.remove(observer);
    }
  }

  @Override public void notifySkinUpdate() {
    if (mSkinObservers == null) return;
    for (ISkinUpdate observer : mSkinObservers) {
      observer.onThemeUpdate();
    }
  }

  public void loadSkin() {
    String skin = SkinConfig.getCustomSkinPath(context);
    loadSkin(skin, null);
  }

  public void loadSkin(ILoaderListener callback) {
    String skin = SkinConfig.getCustomSkinPath(context);
    if (SkinConfig.isDefaultSkin(context)) {
      return;
    }
    loadSkin(skin, callback);
  }

  /**
   * load skin form local
   * <p>
   * eg:theme.skin
   * </p>
   *
   * @param skinName the name of skin(in assets/skin)
   * @param callback load Callback
   */
  public void loadSkin(String skinName, final ILoaderListener callback) {

    new AsyncTask<String, Void, Resources>() {

      @Override protected void onPreExecute() {
        if (callback != null) {
          callback.onStart();
        }
      }

      @Override protected Resources doInBackground(String... params) {
        Resources skinResource = null;
        try {
          if (params.length == 1) {
            String skinPkgPath = SkinFileUtils.getSkinDir(context) + File.separator + params[0];
            SkinL.i("skinPkgPath", skinPkgPath);
            File file = new File(skinPkgPath);
            if (!file.exists()) {
              return null;
            }
            PackageManager mPm = context.getPackageManager();
            PackageInfo mInfo =
                mPm.getPackageArchiveInfo(skinPkgPath, PackageManager.GET_ACTIVITIES);
            skinPackageName = mInfo.packageName;
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, skinPkgPath);

            Resources superRes = context.getResources();
            skinResource = new Resources(assetManager, superRes.getDisplayMetrics(),
                superRes.getConfiguration());

            SkinConfig.saveSkinPath(context, params[0]);

            skinPath = skinPkgPath;
            isDefaultSkin = false;
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          return skinResource;
        }
      }

      @Override protected void onPostExecute(Resources result) {
        mResources = result;

        if (mResources != null) {
          if (callback != null) callback.onSuccess();
          notifySkinUpdate();
        } else {
          isDefaultSkin = true;
          if (callback != null) callback.onFailed("没有获取到资源");
        }
      }
    }.execute(skinName);
  }

  /**
   * load skin form internet
   * <p>
   * eg:https://raw.githubusercontent.com/burgessjp/ThemeSkinning/master/app/src/main/assets/skin/theme.skin
   * </p>
   *
   * @param skinUrl the url of skin
   * @param callback load Callback
   */
  public void loadSkinFromUrl(String skinUrl, final ILoaderListener callback) {
    String skinPath = SkinFileUtils.getSkinDir(context);
    final String skinName = skinUrl.substring(skinUrl.lastIndexOf("/") + 1);
    String skinFullName = skinPath + File.separator + skinName;
    File skinFile = new File(skinFullName);
    if (skinFile.exists()) {
      loadSkin(skinName, callback);
      return;
    }

    Uri downloadUri = Uri.parse(skinUrl);
    Uri destinationUri = Uri.parse(skinFullName);

    DownloadRequest downloadRequest =
        new DownloadRequest(downloadUri).setRetryPolicy(new DefaultRetryPolicy())
            .setDestinationURI(destinationUri)
            .setPriority(DownloadRequest.Priority.HIGH);
    callback.onStart();
    downloadRequest.setStatusListener(new DownloadStatusListenerV1() {
      @Override public void onDownloadComplete(DownloadRequest downloadRequest) {
        loadSkin(skinName, callback);
      }

      @Override public void onDownloadFailed(DownloadRequest downloadRequest, int errorCode,
          String errorMessage) {
        callback.onFailed(errorMessage);
      }

      @Override
      public void onProgress(DownloadRequest downloadRequest, long totalBytes, long downloadedBytes,
          int progress) {
        callback.onProgress(progress);
      }
    });

    ThinDownloadManager manager = new ThinDownloadManager();
    manager.add(downloadRequest);
  }

  public void loadFont(String fontName) {
    Typeface tf = TypefaceUtils.createTypeface(context, fontName);
    TextViewRepository.applyFont(tf);
  }

  @ColorInt public int getColor(@ColorRes int resId) {
    int originColor = context.getResources().getColor(resId);
    if (mResources == null || isDefaultSkin) {
      return originColor;
    }

    String resName = context.getResources().getResourceEntryName(resId);

    int trueResId = mResources.getIdentifier(resName, "color", skinPackageName);
    int trueColor;

    try {
      trueColor = mResources.getColor(trueResId);
    } catch (Resources.NotFoundException e) {
      e.printStackTrace();
      trueColor = originColor;
    }

    return trueColor;
  }

  public Drawable getDrawable(@DrawableRes int resId) {
    Drawable originDrawable = context.getResources().getDrawable(resId);
    if (mResources == null || isDefaultSkin) {
      return originDrawable;
    }
    String resName = context.getResources().getResourceEntryName(resId);

    int trueResId = mResources.getIdentifier(resName, "drawable", skinPackageName);

    Drawable trueDrawable;
    try {
      SkinL.i("SkinManager getDrawable", "SDK_INT = " + android.os.Build.VERSION.SDK_INT);
      if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
        trueDrawable = mResources.getDrawable(trueResId);
      } else {
        trueDrawable = mResources.getDrawable(trueResId, null);
      }
    } catch (Resources.NotFoundException e) {
      e.printStackTrace();
      trueDrawable = originDrawable;
    }

    return trueDrawable;
  }

  /**
   * 加载指定资源颜色drawable,转化为ColorStateList，保证selector类型的Color也能被转换。
   * 无皮肤包资源返回默认主题颜色
   *
   * @param resId
   * @return
   * @author pinotao
   */
  public ColorStateList getColorStateList(@ColorRes int resId) {
    boolean isExtendSkin = true;
    if (mResources == null || isDefaultSkin) {
      isExtendSkin = false;
    }

    String resName = context.getResources().getResourceEntryName(resId);
    if (isExtendSkin) {
      int trueResId = mResources.getIdentifier(resName, "color", skinPackageName);
      ColorStateList trueColorList;
      if (trueResId == 0) { // 如果皮肤包没有复写该资源，但是需要判断是否是ColorStateList
        try {
          ColorStateList originColorList = context.getResources().getColorStateList(resId);
          return originColorList;
        } catch (Resources.NotFoundException e) {
          e.printStackTrace();
          SkinL.e("resName = " + resName + " NotFoundException : " + e.getMessage());
        }
      } else {
        try {
          trueColorList = mResources.getColorStateList(trueResId);
          return trueColorList;
        } catch (Resources.NotFoundException e) {
          e.printStackTrace();
          SkinL.e("resName = " + resName + " NotFoundException :" + e.getMessage());
        }
      }
    } else {
      try {
        ColorStateList originColorList = context.getResources().getColorStateList(resId);
        return originColorList;
      } catch (Resources.NotFoundException e) {
        e.printStackTrace();
        SkinL.e("resName = " + resName + " NotFoundException :" + e.getMessage());
      }
    }

    int[][] states = new int[1][1];
    return new ColorStateList(states, new int[] { context.getResources().getColor(resId) });
  }
}
