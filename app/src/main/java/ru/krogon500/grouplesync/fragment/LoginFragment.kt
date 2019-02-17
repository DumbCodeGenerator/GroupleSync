package ru.krogon500.grouplesync.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.activity_grouple_login.*
import ru.krogon500.grouplesync.R
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.interfaces.LoginListener

class LoginFragment: Fragment {

    internal lateinit var mSettings: SharedPreferences

    private lateinit var listener: LoginListener
    internal var type: Byte = 0

    private var mAuthTask: UserLoginTask? = null

    constructor()

    @SuppressLint("ValidFragment")
    constructor(listener: LoginListener, type: Byte) {
        this.listener = listener
        this.type = type
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(
                R.layout.activity_grouple_login, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mSettings = PreferenceManager.getDefaultSharedPreferences(activity)
        password.setOnEditorActionListener { _, actionId, _ ->  onEditor(actionId)}
        email_sign_in_button.setOnClickListener {
            Utils.toggleKeyboard(context)
            attemptLogin()
        }
    }

    private fun onEditor(id: Int): Boolean {
        if (id == EditorInfo.IME_ACTION_DONE) {
            Utils.toggleKeyboard(context)
            attemptLogin()
            return true
        }
        return false
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        if (mAuthTask != null) {
            return
        }

        // Reset errors.
        email.error = null
        password.error = null

        // Store values at the time of the login attempt.
        val email = email.text.toString()
        val password = password.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            this.password.error = getString(R.string.error_invalid_password)
            focusView = this.password
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            this.email.error = getString(R.string.error_field_required)
            focusView = this.email
            cancel = true
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView?.requestFocus()
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true)
            mAuthTask = UserLoginTask(email, password)
            mAuthTask?.execute()
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private fun showProgress(show: Boolean) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        //login_form.visibility = if (show) View.GONE else View.VISIBLE
        login_form.animate().setDuration(shortAnimTime.toLong()).alpha(
                (if (show) 0 else 1).toFloat()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                login_form.visibility = if (show) View.GONE else View.VISIBLE
            }
        })

        //login_progress!!.visibility = if (show) View.VISIBLE else View.GONE
        login_progress.animate().setDuration(shortAnimTime.toLong()).alpha(
                (if (show) 1 else 0).toFloat()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                login_progress.visibility = if (show) View.VISIBLE else View.GONE
            }
        })
    }

    private fun startMain(user: String, pass: String) {
        val args = Bundle()
        args.putString("user", user)
        args.putString("pass", pass)

        listener.goToMain(type, args)
    }

    @SuppressLint("StaticFieldLeak")
    private inner class UserLoginTask internal constructor(private val mUser: String, private val mPassword: String) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void): Boolean? {
            return Utils.login(type, mUser, mPassword)
        }

        override fun onPostExecute(success: Boolean) {
            mAuthTask = null
            if (success) {
                if (remember.isChecked && type == Utils.GROUPLE) {
                    mSettings.edit().putBoolean("remember", true)
                            .putString("user", mUser)
                            .putString("pass", mPassword).apply()

                } else if (remember.isChecked && type == Utils.HENTAI) {
                    mSettings.edit().putBoolean("remember_h", true)
                            .putString("user_h", mUser)
                            .putString("pass_h", mPassword).apply()
                }
                startMain(mUser, mPassword)
            } else {
                showProgress(false)
                password.error = getString(R.string.error_incorrect_password)
                password.requestFocus()
            }
        }

        override fun onCancelled() {
            mAuthTask = null
            showProgress(false)
        }
    }
}

