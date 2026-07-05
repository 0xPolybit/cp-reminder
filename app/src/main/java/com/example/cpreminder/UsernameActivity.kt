package com.example.cpreminder

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors

/**
 * Asks the user for their CodeForces handle, verifies it against the CodeForces
 * API, and on success stores it locally before moving on to [MainActivity].
 */
class UsernameActivity : AppCompatActivity() {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var inputLayout: TextInputLayout
    private lateinit var editText: TextInputEditText
    private lateinit var continueButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_username)

        inputLayout = findViewById(R.id.usernameInputLayout)
        editText = findViewById(R.id.usernameEditText)
        continueButton = findViewById(R.id.continueButton)
        progressBar = findViewById(R.id.progressBar)

        continueButton.setOnClickListener { submit() }
    }

    private fun submit() {
        val handle = editText.text?.toString()?.trim().orEmpty()
        if (handle.isEmpty()) {
            inputLayout.error = getString(R.string.username_empty_error)
            return
        }
        inputLayout.error = null
        setLoading(true)

        ioExecutor.execute {
            val result = CodeForcesApi.verifyHandle(handle)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setLoading(false)
                when (result) {
                    is CodeForcesApi.Result.Valid -> onVerified(result.canonicalHandle)
                    is CodeForcesApi.Result.Invalid ->
                        inputLayout.error = getString(R.string.username_not_found)
                    is CodeForcesApi.Result.Error ->
                        Toast.makeText(
                            this,
                            R.string.username_network_error,
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }
    }

    private fun onVerified(handle: String) {
        UserPrefs.saveUsername(this, handle)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        continueButton.isEnabled = !loading
        editText.isEnabled = !loading
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
