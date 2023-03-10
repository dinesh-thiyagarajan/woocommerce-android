package com.woocommerce.android.ui.message

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.R
import com.woocommerce.android.databinding.NotificationsPermissionsBarBinding
import com.woocommerce.android.util.WooAnimUtils
import com.woocommerce.android.util.WooPermissionUtils

class NotificationsPermissionBarView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : CardView(ctx, attrs) {
    private val activity = context as AppCompatActivity
    private val binding = NotificationsPermissionsBarBinding.bind(View.inflate(
        context,
        R.layout.notifications_permissions_bar,
        this
    ))

    private val shouldShowNotificationsPermissionBar: Boolean
        get() {
            return VERSION.SDK_INT >= VERSION_CODES.TIRAMISU &&
                !WooPermissionUtils.hasNotificationsPermission(activity) &&
                !AppPrefs.getWasNotificationsPermissionBarDismissed()
        }

    private val launcher = activity.registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            hide()
        }
    }

    init {
//        addView(this)

        postDelayed(
            {
                if (shouldShowNotificationsPermissionBar) {
                    show()
                } else {
                    hide()
                }
            },
            2000
        )

        binding.btnAllow.setOnClickListener {
            requestNotificationsPermission()
        }

        binding.btnDismiss.setOnClickListener {
            AppPrefs.setWasNotificationsPermissionBarDismissed(true)
            hide()
        }
    }

    fun show() {
        WooAnimUtils.animateBottomBar(this, show = true)
    }

    fun hide() {
        WooAnimUtils.animateBottomBar(this, show = false)
    }

    private fun requestNotificationsPermission() {
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            WooPermissionUtils.requestNotificationsPermission(launcher)
        }
    }
}
