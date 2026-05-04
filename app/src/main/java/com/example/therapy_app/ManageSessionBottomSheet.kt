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
    private val onDeleteSession: (String) -> Unit,
    private val onUpdated: () -> Unit
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

    private fun loadSessionAndOpenDialog() {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("sessions")
            .document(sessionId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val title = document.getString("title") ?: ""
                    val tagsList = document.get("tags") as? List<*>

                    // 🔥 FIX: Normalize all tags to lowercase when loading
                    val tagsSet = tagsList
                        ?.mapNotNull { it?.toString()?.lowercase()?.trim() }
                        ?.toMutableSet()
                        ?: mutableSetOf()

                    openManageTagsDialog(
                        currentTitle = title,
                        existingTags = tagsSet
                    )
                }
            }
    }

    private fun openManageTagsDialog(
        currentTitle: String,
        existingTags: MutableSet<String>
    ) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tags, null)
        val titleInput = view.findViewById<TextInputEditText>(R.id.titleInput)
        val chipGroup = view.findViewById<ChipGroup>(R.id.tagChipGroup)
        val customInput = view.findViewById<TextInputEditText>(R.id.customTagInput)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveTagsButton)

        // Standardized lowercase list
        val hardcodedTags = listOf("anxiety", "depression", "mindfulness", "trauma", "cbt")
        titleInput.setText(currentTitle)

        fun createChip(text: String, isChecked: Boolean = false): Chip {
            return Chip(requireContext()).apply {
                this.text = text
                this.isCheckable = true
                this.isChecked = isChecked
                setChipBackgroundColorResource(R.color.red_dark)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                rippleColor = ColorStateList.valueOf(Color.parseColor("#8B0000"))
            }
        }

        // 1. HARDCODED TAGS: Case-insensitive match check
        hardcodedTags.forEach { tag ->
            val match = existingTags.contains(tag.lowercase())
            val chip = createChip(tag, match)

            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) existingTags.add(tag.lowercase())
                else existingTags.remove(tag.lowercase())
            }
            chipGroup.addView(chip)
        }

        // 2. CUSTOM TAGS: Display existing tags not in the hardcoded list
        existingTags.filter { it !in hardcodedTags }.forEach { tag ->
            val chip = createChip(tag, true).apply {
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    existingTags.remove(tag)
                    chipGroup.removeView(this)
                }
            }
            chipGroup.addView(chip)
        }

        // 3. PRE-CREATE DIALOG so saveButton can reference it
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()

        // 4. ADD NEW CUSTOM TAG
        customInput.setOnEditorActionListener { _, _, _ ->
            val text = customInput.text.toString().trim().lowercase()
            if (text.isNotEmpty() && !existingTags.contains(text)) {
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

        // 5. SAVE BUTTON: Correctly updates Firebase
        saveButton.setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val updatedTitle = titleInput.text.toString().trim()

            if (updatedTitle.isEmpty()) {
                Toast.makeText(requireContext(), "Title required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pendingText = customInput.text.toString().trim().lowercase()

            if (pendingText.isNotEmpty() && !existingTags.contains(pendingText)) {
                existingTags.add(pendingText)
            }

            val updatedData = mapOf(
                "title" to updatedTitle,
                "tags" to existingTags.toList()
            )

            FirebaseFirestore.getInstance().collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .set(updatedData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Session updated", Toast.LENGTH_SHORT).show()
                    onUpdated()      // Refresh TherapyActivity
                    dialog.dismiss()  // Close edit dialog
                    dismiss()         // Close bottom sheet
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun showDeleteConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Session")
            .setMessage("Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                onDeleteSession(sessionId)
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}