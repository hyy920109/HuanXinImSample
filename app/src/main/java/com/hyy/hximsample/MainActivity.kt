package com.hyy.hximsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.hyphenate.easeim.MainActivity
import com.hyphenate.easeim.common.enums.Status
import com.hyphenate.easeim.common.interfaceOrImplement.OnResourceParseCallback
import com.hyphenate.easeim.common.net.Resource
import com.hyphenate.easeim.common.utils.ToastUtils.showToast
import com.hyphenate.easeim.section.login.activity.LoginActivity
import com.hyphenate.easeim.section.login.viewmodels.SplashViewModel
import com.hyphenate.util.EMLog
import com.hyy.hximsample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private val model: SplashViewModel by lazy {
        ViewModelProvider(this).get(SplashViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.startIm.setOnClickListener {
            loginSDK()
        }
    }
    private fun loginSDK() {
        model.getLoginData().observe(this,
            Observer { response: Resource<Boolean> ->
                parseResource<Boolean>(
                    response,
                    object : OnResourceParseCallback<Boolean>(true) {
                        override fun onSuccess(data: Boolean?) {
                            MainActivity.startAction(this@MainActivity)
                            finish()
                        }

                        override fun onError(code: Int, message: String) {
                            super.onError(code, message)
                            EMLog.i("TAG", "error message = " + response.message)
                            LoginActivity.startAction(this@MainActivity)
                            finish()
                        }
                    })
            })
    }

    /**
     * 解析Resource<T>
     * @param response
     * @param callback
     * @param <T>
    </T></T> */
    fun <T> parseResource(response: Resource<T>?, callback: OnResourceParseCallback<T>) {
        if (response == null) {
            return
        }
        if (response.status == Status.SUCCESS) {
            callback.hideLoading()
            callback.onSuccess(response.data)
        } else if (response.status == Status.ERROR) {
            callback.hideLoading()
            if (!callback.hideErrorMsg) {
                //showToast(response.message)
            }
            callback.onError(response.errorCode, response.message)
        } else if (response.status == Status.LOADING) {
            callback.onLoading(response.data)
        }
    }
}