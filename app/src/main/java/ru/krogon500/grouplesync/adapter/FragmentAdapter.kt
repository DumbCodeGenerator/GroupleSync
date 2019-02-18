package ru.krogon500.grouplesync.adapter

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import ru.krogon500.grouplesync.Utils
import ru.krogon500.grouplesync.fragment.GroupleFragment
import ru.krogon500.grouplesync.fragment.HentaiFragment
import ru.krogon500.grouplesync.fragment.LoginFragment
import ru.krogon500.grouplesync.interfaces.LoginListener

class FragmentAdapter(private val mFragmentManager: FragmentManager, //private SparseArray<WeakReference<Fragment>> registeredFragments = new SparseArray<WeakReference<Fragment>>();
                      private val mSettings: SharedPreferences) : FragmentPagerAdapter(mFragmentManager) {
    private val titles = arrayOf("Grouple", "Hentaichan")
    private var mFragmentAtPos0: Fragment? = null
    private var mFragmentAtPos1: Fragment? = null
    private val listener = LoginList()
    private var groupleLogged: Boolean = false
    private var hentaiLogged: Boolean = false

    fun goBack(type: Byte?) {
        when (type) {
            Utils.GROUPLE -> {
                mFragmentManager.beginTransaction().remove(mFragmentAtPos0!!)
                        .commitNowAllowingStateLoss()
                mFragmentAtPos0 = LoginFragment(listener, type)
            }
            Utils.HENTAI -> {
                mFragmentManager.beginTransaction().remove(mFragmentAtPos1!!)
                        .commitNowAllowingStateLoss()
                mFragmentAtPos1 = LoginFragment(listener, type)
            }
        }
        notifyDataSetChanged()
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return titles[position]
    }

    override fun getItem(position: Int): Fragment {
        if (position == 0) {
            if (mFragmentAtPos0 == null) {
                if (mSettings.getBoolean("remember", false)) {
                    val user = mSettings.getString("user", "")
                    val pass = mSettings.getString("pass", "")
                    val args = Bundle()
                    args.putString("user", user)
                    args.putString("pass", pass)
                    val fragment = GroupleFragment()
                    fragment.arguments = args
                    mFragmentAtPos0 = fragment
                } else {
                    mFragmentAtPos0 = LoginFragment(listener, Utils.GROUPLE)
                }
                groupleLogged = mFragmentAtPos0 !is LoginFragment
                return mFragmentAtPos0 as Fragment
            } else {
                groupleLogged = mFragmentAtPos0 !is LoginFragment
                return mFragmentAtPos0 as Fragment
            }

        } else {
            if (mFragmentAtPos1 == null) {
                if (mSettings.getBoolean("remember_h", false)) {
                    val user = mSettings.getString("user_h", "")
                    val pass = mSettings.getString("pass_h", "")
                    val args = Bundle()
                    args.putString("user", user)
                    args.putString("pass", pass)
                    val fragment = HentaiFragment()
                    fragment.arguments = args
                    mFragmentAtPos1 = fragment
                } else {
                    mFragmentAtPos1 = LoginFragment(listener, Utils.HENTAI)
                }
                hentaiLogged = mFragmentAtPos1 !is LoginFragment
                return mFragmentAtPos1 as Fragment
            } else {
                hentaiLogged = mFragmentAtPos1 !is LoginFragment
                return mFragmentAtPos1 as Fragment
            }
        }
    }

    override fun getItemPosition(`object`: Any): Int {
        if (`object` is LoginFragment && mFragmentAtPos0 is GroupleFragment && !groupleLogged)
            return POSITION_NONE
        if (`object` is GroupleFragment && mFragmentAtPos0 is LoginFragment && groupleLogged)
            return POSITION_NONE
        if (`object` is LoginFragment && mFragmentAtPos1 is HentaiFragment && !hentaiLogged)
            return POSITION_NONE
        return if (`object` is HentaiFragment && mFragmentAtPos1 is LoginFragment && hentaiLogged)
            POSITION_NONE
        else
            POSITION_UNCHANGED
    }

    override fun getCount(): Int {
        return FragmentAdapter.NUM_ITEMS
    }

    inner class LoginList : LoginListener {
        override fun goToMain(type: Byte?, args: Bundle) {
            if (type == Utils.GROUPLE) {
                mFragmentManager.beginTransaction().remove(mFragmentAtPos0!!)
                        .commitNowAllowingStateLoss()
                mFragmentAtPos0 = GroupleFragment()
                mFragmentAtPos0!!.arguments = args
            } else if (type == Utils.HENTAI) {
                mFragmentManager.beginTransaction().remove(mFragmentAtPos1!!)
                        .commitNowAllowingStateLoss()
                mFragmentAtPos1 = HentaiFragment()
                mFragmentAtPos1!!.arguments = args
            }
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val NUM_ITEMS = 2
    }
}
