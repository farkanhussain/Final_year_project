package com.example.therapy_app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class ManageSessionBottomSheet(
    private val sessionId: String,
    private val onDeleteSession: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_manage_session, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.optionView).setOnClickListener {
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("SESSION_ID", sessionId)
            startActivity(intent)
            dismiss()
        }

        view.findViewById<TextView>(R.id.optionManageSessionDetails).setOnClickListener {
            loadSessionAndOpenDialog()
        }

        view.findViewById<TextView>(R.id.optionDelete).setOnClickListener {
            showDeleteConfirmation()
        }
    }

    // -------------------------------
    // LOAD SESSION
    // -------------------------------
    private fun loadSessionAndOpenDialog() {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users")
            .document(userId)
            .collection("sessions")
            .document(sessionId)
            .get()
            .addOnSuccessListener { document ->

                if (document.exists()) {
                    val title = document.getString("title") ?: ""

                    val tagsList = document.get("tags") as? List<*>
                    val tagsSet = tagsList
                        ?.mapNotNull { it?.toString() }
                        ?.toMutableSet()
                        ?: mutableSetOf()

                    openManageTagsDialog(
                        currentTitle = title,
                        existingTags = tagsSet
                    )
                } else {
                    Toast.makeText(requireContext(), "Session not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // -------------------------------
    // TAG + TITLE DIALOG
    // -------------------------------
    private fun openManageTagsDialog(
        currentTitle: String,
        existingTags: MutableSet<String>
    ) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_tags, null)

        val titleInput = view.findViewById<TextInputEditText>(R.id.titleInput)
        val chipGroup = view.findViewById<ChipGroup>(R.id.tagChipGroup)
        val customInput = view.findViewById<TextInputEditText>(R.id.customTagInput)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveTagsButton)

        val hardcodedTags = listOf(
            "anxiety",
            "depression",
            "mindfulness",
            "trauma",
            "CBT"
        )

        titleInput.setText(currentTitle)

        // -------------------------------
        // CHIP FACTORY (FIXED + MATERIAL RIPPLE ENABLED)
        // -------------------------------
        fun createChip(text: String, isChecked: Boolean = false): Chip {
            return Chip(requireContext()).apply {
                this.text = text
                isCheckable = true
                this.isChecked = isChecked
                isClickable = true

                // Background selector (red_dark ↔ red)
                setChipBackgroundColorResource(R.color.red_dark)

                // Text
                setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )

                // Stroke
                chipStrokeColor = ContextCompat.getColorStateList(
                    requireContext(),
                    R.color.red_dark
                )
                chipStrokeWidth = 1f

                // IMPORTANT:
                // DO NOT override rippleColor → enables native Material ripple (red flash if theme allows)

                rippleColor = ColorStateList.valueOf(
                    Color.parseColor("#8B0000")
                )


            }
        }

        // -------------------------------
        // HARD CODED TAGS
        // -------------------------------
        hardcodedTags.forEach { tag ->
            val chip = createChip(tag, existingTags.contains(tag))

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) existingTags.add(tag)
                else existingTags.remove(tag)
            }

            chipGroup.addView(chip)
        }

        // -------------------------------
        // CUSTOM TAGS (existing)
        // -------------------------------
        val customTags = existingTags.filter { tag ->
            hardcodedTags.none { it.equals(tag, ignoreCase = true) }
        }

        customTags.forEach { tag ->
            val chip = createChip(tag, true).apply {
                isCloseIconVisible = true

                setOnCloseIconClickListener {
                    existingTags.remove(tag)
                    chipGroup.removeView(this)
                }
            }

            chipGroup.addView(chip)
        }

        // -------------------------------
        // ADD NEW CUSTOM TAG
        // -------------------------------
        customInput.setOnEditorActionListener { _, _, _ ->
            val text = customInput.text.toString().trim()

            if (text.isNotEmpty() &&
                existingTags.none { it.equals(text, ignoreCase = true) }) {

                val chip = createChip(text, true).apply {
                    isCloseIconVisible = true

                    setOnCloseIconClickListener {
                        existingTags.remove(text)
                        chipGroup.removeView(this)
                    }
                }

                chipGroup.addView(chip)
                existingTags.add(text)
                customInput.text?.clear()
            }

            true
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()

        // -------------------------------
        // SAVE
        // -------------------------------
        saveButton.setOnClickListener {

            val userId = FirebaseAuth.getInstance().currentUser?.uid

            if (userId == null) {
                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedTitle = titleInput.text.toString().trim()

            if (updatedTitle.isEmpty()) {
                Toast.makeText(requireContext(), "Title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cleanedTags = existingTags
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .associateBy { it.lowercase() }
                .values
                .toSet()

            val db = FirebaseFirestore.getInstance()

            val updatedData = hashMapOf(
                "title" to updatedTitle,
                "tags" to cleanedTags.toList()
            )

            db.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .update(updatedData as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Session updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        dialog.show()
    }

    // -------------------------------
    // DELETE CONFIRMATION
    // -------------------------------
    private fun showDeleteConfirmation() {
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Session")
            .setMessage("Are you sure you want to delete this session? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->

                onDeleteSession(sessionId)

                Toast.makeText(
                    requireContext(),
                    "Session deleted successfully",
                    Toast.LENGTH_SHORT
                ).show()

                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            resources.getColor(android.R.color.holo_red_dark, null)
        )

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            resources.getColor(android.R.color.black, null)
        )
    }
}