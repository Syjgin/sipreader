package com.syjgin.sipreader;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Created by syjgin on 07.08.15.
 */
public class PasswordTextPreference extends com.syjgin.sipreader.EditTextPreference {
    public PasswordTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        if(this.getText() != null)
            return "*****";
        return "";
    }
}
