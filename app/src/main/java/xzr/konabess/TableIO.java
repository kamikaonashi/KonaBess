package xzr.konabess;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;

import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;

public class TableIO {
    private static boolean decodeAndWriteData(Activity activity, String data) throws Exception{
        if(!data.startsWith("konabess://"))
            return true;
        data=data.replace("konabess://","");
        String decoded_data=new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);

        String[] lines=decoded_data.split("\n");
        if(!ChipInfo.which.toString().equals(lines[0]))
            return true;
        boolean freq_started=false;
        boolean volt_started=false;
        ArrayList<String> freq=new ArrayList<>();
        ArrayList<String> volt=new ArrayList<>();
        for(String line:lines){
            if(line.equals("#Freq start")){
                freq_started=true;
                continue;
            }
            if(line.equals("#Freq end")){
                freq_started=false;
                continue;
            }
            if(line.equals("#Volt start")){
                volt_started=true;
                continue;
            }
            if(line.equals("#Volt end")){
                volt_started=false;
                continue;
            }
            if(freq_started) {
                freq.add(line);
                continue;
            }
            if(volt_started){
                volt.add(line);
            }
        }

        GpuTableEditor.writeOut(GpuTableEditor.genBack(freq));
        if(ChipInfo.which!= ChipInfo.type.lahaina_singleBin){
            //Init again because the dts file has been updated
            GpuVoltEditor.init();
            GpuVoltEditor.decode();
            GpuVoltEditor.writeOut(GpuVoltEditor.genBack(volt));
        }

        return false;
    }

    private static String getAndEncodeData(){
        StringBuilder data= new StringBuilder();
        data.append(ChipInfo.which).append("\n");
        data.append("#Freq start\n");
        for(String line:GpuTableEditor.genTable()){
            data.append(line).append("\n");
        }
        data.append("#Freq end\n");
        if(ChipInfo.which!= ChipInfo.type.lahaina_singleBin){
            data.append("#Volt start\n");
            for(String line:GpuVoltEditor.genTable()){
                data.append(line).append("\n");
            }
            data.append("#Volt end\n");
        }
        return "konabess://"+Base64.getEncoder().encodeToString(data.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean error;
    private static void import_edittext(Activity activity){
        EditText editText=new EditText(activity);
        editText.setHint("在这里粘贴频率和电压信息");

        new AlertDialog.Builder(activity)
                .setTitle("导入")
                .setView(editText)
                .setPositiveButton("确认", (dialog, which) -> {
                    AlertDialog waiting=DialogUtil.getWaitDialog(activity,"正在导入数据，请稍后");
                    waiting.show();

                    new Thread(() -> {
                        error=false;
                        try {
                            error=decodeAndWriteData(activity,editText.getText().toString());
                        } catch (Exception e) {
                            error=true;
                        }
                        activity.runOnUiThread(() -> {
                            waiting.dismiss();
                            if(!error)
                                Toast.makeText(activity,"导入成功",Toast.LENGTH_SHORT).show();
                            else
                                Toast.makeText(activity,"导入失败，数据可能无效或与设备不兼容",Toast.LENGTH_SHORT).show();
                        });
                    }).start();

                })
                .setNegativeButton("取消",null)
                .create().show();
    }

    private static void export_cpy(Activity activity){
        DialogUtil.showDetailedInfo(activity,"导出完毕","以下是导出的频率和电压内容", getAndEncodeData());
    }

    private static class exportToFile extends Thread{
        Activity activity;
        public exportToFile(Activity activity){
            this.activity=activity;
        }
        public void run(){
            error=false;
            File out=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/konabess-"+new SimpleDateFormat("MMddHHmmss").format(new Date())+".txt");
            try {
                BufferedWriter bufferedWriter=new BufferedWriter(new FileWriter(out));
                bufferedWriter.write(getAndEncodeData());
                bufferedWriter.close();
            } catch (IOException e) {
                error=true;
            }
            activity.runOnUiThread(() -> {
                if(!error)
                    Toast.makeText(activity,"成功导出到"+out.getAbsolutePath(),Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(activity,"导出失败",Toast.LENGTH_SHORT).show();
            });
        }
    }

    private static class importFromFile extends MainActivity.fileWorker{
        Activity activity;
        AlertDialog waiting;
        public importFromFile(Activity activity){
            this.activity=activity;
        }
        public void run(){
            if(uri==null)
                return;
            error=false;
            activity.runOnUiThread(() -> {
                waiting=DialogUtil.getWaitDialog(activity,"正在导入数据，请稍后");
                waiting.show();
            });
            try {
                BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(activity.getContentResolver().openInputStream(uri)));
                error=decodeAndWriteData(activity,bufferedReader.readLine());
                bufferedReader.close();
            }  catch (Exception e) {
                error=true;
            }
            activity.runOnUiThread(() -> {
                waiting.dismiss();
                if(!error)
                    Toast.makeText(activity,"导入成功",Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(activity,"导入失败，数据可能无效或与设备不兼容",Toast.LENGTH_SHORT).show();
            });

        }
    }

    private static void generateView(Activity activity, LinearLayout page) {
        ((MainActivity)activity).onBackPressedListener=new MainActivity.onBackPressedListener(){
            @Override
            public void onBackPressed() {
                ((MainActivity)activity).showMainView();
            }
        };

        ListView listView=new ListView(activity);
        ArrayList<ParamAdapter.item> items=new ArrayList<>();

        items.add(new ParamAdapter.item(){{
            title="从剪贴板导入";
            subtitle="从剪贴板导入外部频率与电压参数";
        }});

        items.add(new ParamAdapter.item(){{
            title="导出到剪贴板";
            subtitle="导出当前频率和电压参数到剪贴板";
        }});

        items.add(new ParamAdapter.item(){{
            title="从文件导入";
            subtitle="从文件导入外部频率与电压参数";
        }});

        items.add(new ParamAdapter.item(){{
            title="导出到文件";
            subtitle="导出当前频率和电压参数到文件";
        }});

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if(position==0){
                import_edittext(activity);
            }
            else if(position==1){
                export_cpy(activity);
            }
            else if(position==2){
                MainActivity.runWithFilePath(activity,new importFromFile(activity));
            }
            else if(position==3){
                MainActivity.runWithStoragePermission(activity,new exportToFile(activity));
            }
        });

        listView.setAdapter(new ParamAdapter(items,activity));

        page.removeAllViews();
        page.addView(listView);
    }

    static class TableIOLogic extends Thread{
        Activity activity;
        AlertDialog waiting;
        LinearLayout showedView;
        LinearLayout page;
        public TableIOLogic(Activity activity, LinearLayout showedView){
            this.activity=activity;
            this.showedView=showedView;
        }
        public void run(){
            activity.runOnUiThread(() -> {
                waiting=DialogUtil.getWaitDialog(activity,"正在准备进行备份还原");
                waiting.show();
            });

            try{
                GpuTableEditor.init();
                GpuTableEditor.decode();
                if(ChipInfo.which!= ChipInfo.type.lahaina_singleBin) {
                    GpuVoltEditor.init();
                    GpuVoltEditor.decode();
                }
            }catch (Exception e){
                activity.runOnUiThread(() -> DialogUtil.showError(activity,"加载频率电压数据失败"));
            }

            activity.runOnUiThread(() -> {
                waiting.dismiss();
                showedView.removeAllViews();
                page=new LinearLayout(activity);
                page.setOrientation(LinearLayout.VERTICAL);
                try {
                    generateView(activity,page);
                } catch (Exception e){
                    DialogUtil.showError(activity,"准备备份还原失败");
                }
                showedView.addView(page);
            });

        }
    }
}