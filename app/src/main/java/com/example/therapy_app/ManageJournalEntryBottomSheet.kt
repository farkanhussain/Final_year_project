package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManageJournalEntryBottomSheet(
    private val entryId: String,
    private val onDeleteEntry: () -> Unit,
    private val onUpdated: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_manage_journal_entry, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.optionViewEdit).setOnClickListener {
            val intent = Intent(requireContext(), JournalEntryActivity::class.java)
            intent.putExtra("ENTRY_ID", entryId)
            startActivity(intent)
            dismiss()
        }

        view.findViewById<TextView>(R.id.optionEditInfo).setOnClickListener {
            // In Journaling context, View/Edit typically handles everything
            val intent = Intent(requireContext(), JournalEntryActivity::class.java)
            intent.putExtra("ENTRY_ID", entryId)
            startActivity(intent)
            dismiss()
        }

        view.findViewById<TextView>(R.id.optionDelete).setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun showDeleteConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this journal entry?")
            .setPositiveButton("Delete") { _, _ ->
                deleteEntry()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
}
