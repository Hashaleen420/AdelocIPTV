package com.adeloc.iptv.cast

import android.view.Menu
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.adeloc.iptv.R

class ExpandedControlsActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.expanded_controller, menu)
        return true
    }
}
