package com.zqc.opencc.android;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.zqc.opencc.android.lib.ChineseConverter;
import com.zqc.opencc.android.lib.ConversionType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    /** Every conversion type the library offers, in the order the spinner shows them. */
    private static final ConversionType[] TYPES = ConversionType.values();

    private ConversionType currentConversionType = ConversionType.TW2SP;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String[] labels = new String[TYPES.length];
        int initialPosition = 0;
        for (int i = 0; i < TYPES.length; i++) {
            labels[i] = TYPES[i].name() + "  " + describe(TYPES[i]);
            if (TYPES[i] == currentConversionType) {
                initialPosition = i;
            }
        }

        Spinner spinner = findViewById(R.id.spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(initialPosition);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentConversionType = TYPES[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final EditText textView = findViewById(R.id.text);

        findViewById(R.id.btn).setOnClickListener(v -> {
            String originalText = textView.getText().toString();
            ConversionType type = currentConversionType;
            executorService.execute(() -> {
                final String converted = ChineseConverter.convert(originalText, type,
                        getApplicationContext());
                textView.post(() -> textView.setText(converted));
            });
        });
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }

    private static String describe(ConversionType type) {
        switch (type) {
            case HK2S:  return "香港繁體到簡體";
            case HK2SP: return "香港繁體到簡體，並轉爲大陸常用詞彙";
            case HK2T:  return "香港繁體到繁體";
            case JP2T:  return "日本漢字到繁體";
            case S2HK:  return "簡體到香港繁體";
            case S2HKP: return "簡體到香港繁體，並轉爲香港常用詞彙";
            case S2T:   return "簡體到繁體";
            case S2TW:  return "簡體到臺灣正體";
            case S2TWP: return "簡體到臺灣正體，並轉爲臺灣常用詞彙";
            case T2HK:  return "繁體到香港繁體";
            case T2S:   return "繁體到簡體";
            case T2TW:  return "繁體到臺灣正體";
            case T2JP:  return "繁體到日本漢字";
            case TW2S:  return "臺灣正體到簡體";
            case TW2T:  return "臺灣正體到繁體";
            case TW2SP: return "臺灣正體到簡體，並轉爲大陸常用詞彙";
            default:    return type.getValue();
        }
    }
}
