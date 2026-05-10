package com.example.therapy_app

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ManageJournalEntryBottomSheet(
    private val entryId: String,
    private val onDeleteEntry: () -> Unit,
    private val onUpdated: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var loadedTitle: String = ""
    private var loadedTags: MutableList<String> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_manage_journal_entry, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadEntryDetails()

        view.findViewById<TextView>(R.id.optionViewEdit).setOnClickListener {
            val intent = Intent(requireContext(), JournalEntryActivity::class.java)
            intent.putExtra("ENTRY_ID", entryId)
            startActivity(intent)
            dismiss()
        }

        view.findViewById<TextView>(R.id.optionEditInfo).setOnClickListener {
            showEditJournalInfoDialog(
                currentTitle = loadedTitle,
                existingTags = loadedTags,
                entryId = entryId
            )
        }


        view.findViewById<TextView>(R.id.optionDelete).setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun showEditJournalInfoDialog(
        currentTitle: String,
        existingTags: MutableList<String>,
        entryId: String
    ) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_journal, null)

        val titleInput = view.findViewById<TextInputEditText>(R.id.titleInput)
        val chipGroup = view.findViewById<ChipGroup>(R.id.tagChipGroup)
        val customInput = view.findViewById<TextInputEditText>(R.id.customTagInput)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveTagsButton)

        // Normalize existing tags to lowercase (CRITICAL FIX)
        val normalizedExisting = existingTags.map { it.lowercase() }.toMutableSet()

        // Hardcoded tags (lowercase)
        val hardcodedTags = listOf(
            "gratitude",
            "reflection",
            "goals",
            "mindfulness",
            "stress",
            "daily"
        )

        // Pre-fill title
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

        // 1️⃣ Hardcoded tags — now correctly pre-checked
        hardcodedTags.forEach { tag ->
            val match = normalizedExisting.contains(tag)
            val chip = createChip(tag, match)

            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) normalizedExisting.add(tag)
                else normalizedExisting.remove(tag)
            }

            chipGroup.addView(chip)
        }

        // 2️⃣ Custom tags — now correctly displayed
        normalizedExisting.filter { it !in hardcodedTags }.forEach { tag ->
            val chip = createChip(tag, true).apply {
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    normalizedExisting.remove(tag)
                    chipGroup.removeView(this)
                }
            }
            chipGroup.addView(chip)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()

        // 3️⃣ Add new custom tag
        customInput.setOnEditorActionListener { _, _, _ ->
            val text = customInput.text.toString().trim().lowercase()
            if (text.isNotEmpty() && !normalizedExisting.contains(text)) {
                val chip = createChip(text, true).apply {
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        normalizedExisting.remove(text)
                        chipGroup.removeView(this)
                    }
                }
                chipGroup.addView(chip)
                normalizedExisting.add(text)
                customInput.text?.clear()
            }
            true
        }

        // 4️⃣ Save
        saveButton.setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val updatedTitle = titleInput.text.toString().trim()

            if (updatedTitle.isEmpty()) {
                Toast.makeText(requireContext(), "Title required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pendingText = customInput.text.toString().trim().lowercase()
            if (pendingText.isNotEmpty()) normalizedExisting.add(pendingText)

            val updatedData = mapOf(
                "title" to updatedTitle,
                "tags" to normalizedExisting.toList()
            )

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("journal_entries")
                .document(entryId)
                .set(updatedData, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Entry updated", Toast.LENGTH_SHORT).show()
                    onUpdated?.invoke()
                    dialog.dismiss()
                    dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }




    private fun showDeleteConfirmation() {

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this journal entry?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEntry()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            // DELETE button → red
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.red))

            // CANCEL button → black
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }

        dialog.show()
    }


    private fun deleteEntry() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("journal_entries")
            .document(entryId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Entry deleted", Toast.LENGTH_SHORT).show()
                onDeleteEntry()
                dismiss()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadEntryDetails() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("journal_entries")
            .document(entryId)
            .get()
            .addOnSuccessListener { doc ->
                loadedTitle = doc.getString("title") ?: ""
                loadedTags = (doc.get("tags") as? List<String>)?.map { it.lowercase() }?.toMutableList()
                    ?: mutableListOf()
            }
    }

}
