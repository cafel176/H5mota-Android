package com.h5mota;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;
import com.h5mota.bbs.BBSActivity;
import com.h5mota.lib.BaseActivity;
import com.h5mota.lib.Constants;
import com.h5mota.lib.Cookies;
import com.h5mota.lib.MyWebServer;
import com.h5mota.lib.Utils;
import com.h5mota.lib.json.JSONObject;
import com.h5mota.lib.subactivity.SubActivity;
import com.h5mota.lib.view.CustomToast;
import fi.iki.elonen.SimpleWebServer;
import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import me.weyye.hipermission.HiPermission;
import me.weyye.hipermission.PermissionCallback;
import me.weyye.hipermission.PermissionItem;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends BaseActivity {
  public File directory;

  SimpleWebServer simpleWebServer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    List<PermissionItem> list = new ArrayList<>();
    list.add(
        new PermissionItem(
            Manifest.permission.WRITE_EXTERNAL_STORAGE, "存储权限", R.drawable.permission_ic_storage));
    list.add(
        new PermissionItem(
            Manifest.permission.READ_PHONE_STATE, "读取手机状态", R.drawable.permission_ic_phone));

    HiPermission.create(this)
        .title("权限申请")
        .permissions(list)
        .msg("你需要如下权限来使用本软件")
        .checkMutiPermission(
            new PermissionCallback() {
              @Override
              public void onClose() {
                Log.i("Main", "onClose");
              }

              @Override
              public void onFinish() {
                Log.i("Main", "onFinish");
                initSDCard();
              }

              @Override
              public void onDeny(String permission, int position) {
                Log.i("Main", "onDeny");
              }

              @Override
              public void onGuarantee(String permission, int position) {
                Log.i("Main", "onGuarantee");
              }
            });

    findViewById(R.id.online)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                loadUrl(Constants.DOMAIN, "HTML5魔塔列表");
              }
            });

    findViewById(R.id.bbs)
        .setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, BBSActivity.class);
                startActivity(intent);
              }
            });

    if (!findViewById(R.id.offline).hasOnClickListeners()) {
      findViewById(R.id.offline)
          .setOnClickListener(
              new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                  new AlertDialog.Builder(MainActivity.this)
                      .setTitle("错误")
                      .setMessage("你没有SD卡的权限！")
                      .setCancelable(true)
                      .setPositiveButton("确定", null)
                      .create()
                      .show();
                }
              });
    }

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  OkHttpClient okHttpClient =
                      new OkHttpClient()
                          .newBuilder()
                          .followRedirects(true)
                          .followSslRedirects(true)
                          .cookieJar(Cookies.getInstance())
                          .build();
                  try (Response response =
                      okHttpClient
                          .newCall(
                              new Request.Builder()
                                  .url(Constants.DOMAIN + "/games/_client/")
                                  .build())
                          .execute()) {
                    String s = response.body().string();
                    JSONObject jsonObject = new JSONObject(s);
                    final JSONObject android = jsonObject.getJSONObject("android");
                    String version = android.getString("version");
                    if (!version.equals(BuildConfig.VERSION_NAME)) {
                      runOnUiThread(
                          new Runnable() {
                            @Override
                            public void run() {
                              try {
                                new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("存在版本更新！")
                                    .setMessage(android.getString("text"))
                                    .setCancelable(true)
                                    .setPositiveButton(
                                        "下载",
                                        new DialogInterface.OnClickListener() {
                                          @Override
                                          public void onClick(
                                              DialogInterface dialogInterface, int i) {
                                            try {
                                              loadUrl(android.getString("url"), "版本更新");
                                            } catch (Exception e) {
                                              e.printStackTrace();
                                            }
                                          }
                                        })
                                    .setNegativeButton("取消", null)
                                    .create()
                                    .show();
                              } catch (Exception e) {
                                e.printStackTrace();
                              }
                            }
                          });
                    }
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }

  @Override
  protected void finishRequest(int type, String string) {}

  private void initSDCard() {

    // check permission
    if (!HiPermission.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) return;

    directory = new File(Environment.getExternalStorageDirectory() + "/H5mota/");

    if (!directory.exists()) {
      directory.mkdirs();
      new Thread(
              new Runnable() {
                @Override
                public void run() {
                  Utils.copyFilesFassets(
                      MainActivity.this, "24层魔塔", directory.getPath() + "/24层魔塔");
                }
              })
          .start();
    }

    try {
      if (simpleWebServer != null) {
        simpleWebServer.stop();
      }
      simpleWebServer = new MyWebServer("127.0.0.1", 1055, directory, true);
      simpleWebServer.start();
    } catch (Exception e) {
      e.printStackTrace();
      simpleWebServer = null;
    }

    findViewById(R.id.offline)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setCancelable(true);
                builder.setTitle("已下载的游戏列表");

                final List<String> names = new ArrayList<>();
                for (File file : directory.listFiles()) {
                  if (new File(file, "index.html").exists()
                      && new File(file, "main.js").exists()
                      && new File(file, "libs").exists()) {
                    names.add(file.getName());
                  }
                }

                builder.setItems(
                    names.toArray(new String[0]),
                    new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialogInterface, int i) {
                        try {
                          String name = names.get(i);
                          loadUrl(Constants.LOCAL + URLEncoder.encode(name, "utf-8"), name);
                        } catch (Exception e) {
                          e.printStackTrace();
                        }
                      }
                    });

                builder.create().show();
              }
            });
  }

  protected void onDestroy() {
    if (simpleWebServer != null) {
      simpleWebServer.stop();
    }
    super.onDestroy();
  }

  double exittime = 0;

  public void wantToExit() {
    if (System.currentTimeMillis() - exittime > 2000) {
      Toast.makeText(this, "再按一遍退出程序", Toast.LENGTH_SHORT).show();
      exittime = System.currentTimeMillis();
    } else {
      exittime = 0;
      setResult(RESULT_CANCELED);
      finish();
    }
  }

  public boolean onCreateOptionsMenu(Menu menu) {
    return super.onCreateOptionsMenu(menu);
  }

  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.clear();
    menu.add(Menu.NONE, 0, 0, "")
        .setIcon(android.R.drawable.ic_menu_set_as)
        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
    menu.add(Menu.NONE, 1, 1, "")
        .setIcon(android.R.drawable.ic_menu_delete)
        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
    menu.add(Menu.NONE, 2, 2, "")
        .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    return true;
  }

  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case 0:
        inputLink();
        break;
      case 1:
        {
          new AlertDialog.Builder(this)
              .setItems(
                  new String[] {"清理在线垃圾存档", "清理离线垃圾存档"},
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      if (i == 0) {
                        loadUrl(Constants.DOMAIN + "/clearStorage.php", "清理在线垃圾存档");
                      } else if (i == 1) {
                        File clearFile = new File(directory, "clearStorage.html");
                        if (!clearFile.exists()) {
                          Utils.copyFilesFassets(
                              MainActivity.this,
                              "clearStorage.html",
                              directory + "/clearStorage.html");
                        }
                        loadUrl(Constants.LOCAL + "clearStorage.html", "清理离线垃圾存档");
                      }
                    }
                  })
              .setTitle("垃圾存档清理工具")
              .setCancelable(true)
              .create()
              .show();
          break;
        }
      case 2:
        finish();
        break;
    }
    return true;
  }

  public void loadUrl(String url, String title) {
    try {
      Intent intent = new Intent(MainActivity.this, WebActivity.class);
      intent.putExtra("title", title);
      intent.putExtra("url", url);
      startActivity(intent);
    } catch (Exception e) {
      e.printStackTrace();
      CustomToast.showErrorToast(this, "无法打开网页！");
    }
  }

  private void inputLink() {
    final EditText editText = new EditText(this);
    editText.setHint("请输入地址...");
    new AlertDialog.Builder(this)
        .setTitle("浏览网页")
        .setView(editText)
        .setPositiveButton(
            "确定",
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                String url = editText.getEditableText().toString();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                  url = "http://" + url;
                }
                loadUrl(url, "浏览网页");
              }
            })
        .setNegativeButton("取消", null)
        .setCancelable(true)
        .create()
        .show();
  }
}
