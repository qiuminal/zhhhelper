package com.qiuminal.zhhhelper;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 虎助手 - 形码编码与拆字查询主页面
 * UI：顶部标题栏 + 渐变搜索栏 + 搜索框 + 结果卡片
 */
public class MainActivity extends AppCompatActivity {

    private EditText etSearch;
    private ImageButton btnClear;
    private ImageButton btnFontMinus;
    private ImageButton btnFontPlus;

    private View resultContainer;      // 结果区整体（标题+卡片）
    private TextView tvChar;            // 字头
    private TextView tvCodes;           // 编码
    private TextView tvRootCodes;       // 字根编码（拆分上方小字）
    private TextView tvComponents;      // 拆分部件
    private TextView tvPinyin;          // 拼音
    private TextView tvUnicode;         // U码
    private View rowZheng;              // 整句码整行
    private TextView tvZhengCode;       // 整句码
    private TextView btnZitong;         // 字统链接
    private TextView btnYedian;         // 叶典链接

    private CharData currentData;       // 当前查询结果，用于字统/叶典跳转

    private float currentFontSp = 18f;  // 结果卡片正文字号
    private static final float MIN_FONT = 14f;
    private static final float MAX_FONT = 28f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化全局内置字体（TumanPUA → 霞鹜文楷 → 遍黑体 P1 → P2）
        AppFonts.init(this);

        // 加载内置数据库
        DataLoader.load(this);

        initViews();
        setupListeners();

        // 全局应用内置字体（标题、静态标签、搜索框提示等）
        AppFonts.applyToHierarchy(findViewById(android.R.id.content));
    }

    private void initViews() {
        etSearch = findViewById(R.id.et_search);
        btnClear = findViewById(R.id.btn_clear);
        btnFontMinus = findViewById(R.id.btn_font_minus);
        btnFontPlus = findViewById(R.id.btn_font_plus);

        resultContainer = findViewById(R.id.result_container);
        tvChar = findViewById(R.id.tv_char);
        tvCodes = findViewById(R.id.tv_codes);
        tvRootCodes = findViewById(R.id.tv_root_codes);
        tvComponents = findViewById(R.id.tv_components);
        tvPinyin = findViewById(R.id.tv_pinyin);
        tvUnicode = findViewById(R.id.tv_unicode);
        rowZheng = findViewById(R.id.row_zheng);
        tvZhengCode = findViewById(R.id.tv_zheng_code);
        btnZitong = findViewById(R.id.btn_zitong);
        btnYedian = findViewById(R.id.btn_yedian);

        // 初始隐藏结果区
        resultContainer.setVisibility(View.GONE);
        btnClear.setVisibility(View.GONE);
    }

    private void setupListeners() {
        // 搜索框实时查询
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                btnClear.setVisibility(input.isEmpty() ? View.GONE : View.VISIBLE);
                if (input.isEmpty()) {
                    resultContainer.setVisibility(View.GONE);
                } else {
                    doQuery(input);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 清除按钮
        btnClear.setOnClickListener(v -> etSearch.setText(""));

        // 字体减小
        btnFontMinus.setOnClickListener(v -> {
            if (currentFontSp > MIN_FONT) {
                currentFontSp -= 2f;
                applyFontSize();
            }
        });

        // 字体增大
        btnFontPlus.setOnClickListener(v -> {
            if (currentFontSp < MAX_FONT) {
                currentFontSp += 2f;
                applyFontSize();
            }
        });

        // 字统网：https://zi.tools/?secondary=zi&word=<字>
        btnZitong.setOnClickListener(v -> openExternalLink("https://zi.tools/?secondary=zi&word="));

        // 叶典：https://www.yedict.com/index.asp?word=<字>
        btnYedian.setOnClickListener(v -> openExternalLink("https://www.yedict.com/index.asp?word="));
    }

    /**
     * 用系统浏览器打开字统/叶典查询当前字
     */
    private void openExternalLink(String baseUrl) {
        if (currentData == null || currentData.charText == null || currentData.charText.isEmpty()) {
            return;
        }
        try {
            Uri uri = Uri.parse(baseUrl + Uri.encode(currentData.charText));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 执行查询
     */
    private void doQuery(String input) {
        CharData data = DataLoader.query(input);
        if (data == null) {
            // 未查到，隐藏结果区
            currentData = null;
            resultContainer.setVisibility(View.GONE);
            return;
        }
        currentData = data;
        bindData(data);
        resultContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 绑定数据到卡片
     */
    private void bindData(CharData d) {
        tvChar.setText(d.charText);
        tvCodes.setText(d.codes != null ? d.codes : "");

        // 字根编码（拆分上方小字），没有则隐藏
        if (d.rootCodes != null && !d.rootCodes.isEmpty()) {
            tvRootCodes.setVisibility(View.VISIBLE);
            tvRootCodes.setText(d.rootCodes);
        } else {
            tvRootCodes.setVisibility(View.GONE);
        }

        tvComponents.setText(d.components != null ? d.components : "");
        tvPinyin.setText(d.pinyin != null ? "(" + d.pinyin + ")" : "");
        tvUnicode.setText(d.unicode != null ? "〔" + d.unicode + "〕" : "");

        // 整句码（zheng.txt），没有则隐藏整行
        if (d.zhengCode != null && !d.zhengCode.isEmpty()) {
            rowZheng.setVisibility(View.VISIBLE);
            tvZhengCode.setText(d.zhengCode);
        } else {
            rowZheng.setVisibility(View.GONE);
        }

        applyFontSize();

        // 结果文本设置完成后，重新应用字符级 fallback 字体
        AppFonts.applyToHierarchy(resultContainer);
    }

    /**
     * 应用字号到结果卡片正文
     */
    private void applyFontSize() {
        tvChar.setTextSize(currentFontSp + 4f);
        tvCodes.setTextSize(currentFontSp);
        tvComponents.setTextSize(currentFontSp);
        tvPinyin.setTextSize(currentFontSp);
        tvUnicode.setTextSize(currentFontSp - 2f);
        tvZhengCode.setTextSize(currentFontSp);
        tvRootCodes.setTextSize(currentFontSp - 4f);
    }
}
