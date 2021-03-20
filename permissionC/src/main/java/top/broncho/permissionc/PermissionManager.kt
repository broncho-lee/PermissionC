package top.broncho.permissionc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class Result

object Granted : Result()

class ShowRational(val list: List<String>) : Result()

class DeniedPermanently(val list: List<String>) : Result()

suspend inline fun AppCompatActivity.request(vararg permissions: String) =
    suspendCoroutine<Result> { continuation ->
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (notGranted.isEmpty()) {
            continuation.resume(Granted)
            return@suspendCoroutine
        }
        ActionFragment.doAction(this, *notGranted) {
            continuation.resume(it)
        }
    }

suspend inline fun Fragment.request(vararg permissions: String) =
    suspendCoroutine<Result> { continuation ->
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (notGranted.isEmpty()) {
            continuation.resume(Granted)
            return@suspendCoroutine
        }
        ActionFragment.doAction(this, *notGranted) {
            continuation.resume(it)
        }
    }

class ActionFragment : Fragment() {
    private var resultCallback: ((Result) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            onResult(Granted)
        } else if (permissions.any { shouldShowRequestPermissionRationale(it) }) {
            onResult(
                ShowRational(
                    permissions.filterIndexed { index, _ ->
                        grantResults[index] == PackageManager.PERMISSION_DENIED
                    }
                )
            )
        } else {
            onResult(
                DeniedPermanently(
                    permissions.filterIndexed { index, _ ->
                        grantResults[index] == PackageManager.PERMISSION_DENIED
                    }
                ))
        }
    }

    fun doAction(resultCallback: (Result) -> Unit, vararg permissions: String) {
        this.resultCallback = resultCallback
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        when {
            notGranted.isEmpty() -> onResult(Granted)
            else -> requestPermissions(
                notGranted,
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun onResult(permissionResult: Result) {
        resultCallback?.invoke(permissionResult)

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        resultCallback = null
    }

    override fun onDestroy() {
        super.onDestroy()
        resultCallback = null
    }

    companion object {

        private const val TAG = "PermissionManager"
        private const val PERMISSIONS_REQUEST_CODE = 42

        fun doAction(
            activity: AppCompatActivity,
            vararg permissions: String,
            resultCallback: (Result) -> Unit
        ) {
            getFragment(
                activity.supportFragmentManager
            ).apply {
                doAction(resultCallback, *permissions)
            }
        }

        fun doAction(
            fragment: Fragment,
            vararg permissions: String,
            resultCallback: (Result) -> Unit
        ) {
            getFragment(
                fragment.childFragmentManager
            ).apply {
                doAction(resultCallback, *permissions)
            }
        }

        private fun getFragment(fragmentManager: FragmentManager): ActionFragment {
            var fragment = fragmentManager.findFragmentByTag(TAG)
            if (fragment != null) {
                fragment = fragment as ActionFragment
            } else {
                fragment = ActionFragment()
                fragmentManager.beginTransaction().add(
                    fragment,
                    TAG
                ).commitNow()
            }
            return fragment
        }

    }

}