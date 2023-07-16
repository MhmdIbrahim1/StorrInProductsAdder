package com.example.productsadder

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.productsadder.databinding.ActivityMainBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val selectedImages = mutableListOf<Uri>()
    private val selectedColors = mutableListOf<Int>()
    private val productStorage = Firebase.storage.reference
    private val firestore = Firebase.firestore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.buttonColorPicker.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Product Color")
                .setPositiveButton("select", object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }
                })
                .setNegativeButton("cancel") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }.show()
        }

        val selectImagesActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val intent = result.data
                    if (intent?.clipData != null) {
                        val count = intent.clipData!!.itemCount
                        for (i in 0 until count) {
                            val imageUri = intent.clipData!!.getItemAt(i).uri
                            selectedImages.add(imageUri)
                        }
                    } else {
                        val imageUri = intent?.data
                        selectedImages.add(imageUri!!)
                    }
                    updateImages()
                }
            }

        binding.buttonImagesPicker.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            selectImagesActivityResult.launch(intent)
        }

    }
    private fun saveProduct() {
        val name = binding.edName.text.toString().trim()
        val price = binding.edPrice.text.toString().trim()
        val category = binding.edCategory.text.toString().trim()
        val description = binding.edDescription.text.toString().trim()
        val offerPercentage = binding.offerPercentage.text.toString().trim()
        val sizes = getSizes(binding.edSizes.text.toString().trim())
        val imagesByteArrays = getImagesByteArrays(selectedImages)
        val images = mutableListOf<String>()

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                showLoading()
            }
            try {
                async {
                    imagesByteArrays.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imageStorage = productStorage.child("products/images/$id")
                            val result = imageStorage.putBytes(it).await()
                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading()
                }
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
            }
            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if (offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                if (description.isEmpty()) null else description,
                if (selectedColors.isEmpty()) null else selectedColors,
                sizes,
                images
            )
            firestore.collection("products").add(product)
                .addOnSuccessListener {
                    hideLoading()
                    Toast.makeText(this@MainActivity, "Product added successfully", Toast.LENGTH_SHORT)
                        .show()
                    resetFields()
                }
                .addOnFailureListener {
                    hideLoading()
                    Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_SHORT).show()
                }
        }

    }
    private fun updateImages() {
        binding.tvSelectedImages.text = selectedImages.size.toString()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.saveProduct) {
            val productValidation = validateInformation()
            if (!productValidation) {
                Toast.makeText(this, "Cech the required fields", Toast.LENGTH_SHORT).show()
            }
            saveProduct()
        }
        return super.onOptionsItemSelected(item)
    }



    private fun hideLoading() {
        binding.progressBar.visibility = View.INVISIBLE
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun getImagesByteArrays(selectedImages: MutableList<Uri>): List<ByteArray> {
        val imagesByteArrays = mutableListOf<ByteArray>()
        selectedImages.forEach {
            val stream = ByteArrayOutputStream()
            val imageBmp = MediaStore.Images.Media.getBitmap(contentResolver, it)
            if (imageBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream))
                imagesByteArrays.add(stream.toByteArray())
        }
        return imagesByteArrays
    }

    private fun getSizes(sizesString: String): List<String>? {
        if (sizesString.isEmpty())
            return null
        val sizesList = sizesString.split(",")
        return sizesList
    }

    private fun validateInformation(): Boolean {
        if (binding.edPrice.text.toString().trim().isEmpty())
            return false

        if (binding.edName.text.toString().trim().isEmpty())
            return false

        if (binding.edCategory.text.toString().trim().isEmpty())
            return false

        if (selectedImages.isEmpty())
            return false

        return true
    }

    private fun updateColors() {
        var colors = ""
        selectedColors.forEach {
            colors = "$colors${Integer.toHexString(it)}"
        }
        binding.tvSelectedColors.text = colors
    }

    private fun resetFields() {
        binding.edName.setText("")
        binding.edPrice.setText("")
        binding.edCategory.setText("")
        binding.edDescription.setText("")
        binding.offerPercentage.setText("")
        binding.edSizes.setText("")
        selectedImages.clear()
        selectedColors.clear()
        updateImages()
        updateColors()
    }
}