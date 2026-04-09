package com.example.therapy_app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ManageSessionBottomSheet(
    private val sessionId: String
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_manage_session, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.optionView).setOnClickListener {
            // open history activity
        }

        view.findViewById<TextView>(R.id.optionContinue).setOnClickListener {
            // continue session
        }

        view.findViewById<TextView>(R.id.optionAddTag).setOnClickListener {
            // open tag dialog
        }

        view.findViewById<TextView>(R.id.optionDelete).setOnClickListener {
            // delete session
        }
    }
}
