package com.example.cwstataccelerator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class ReferenceSheetFragment extends Fragment {

    private MorseCodeGenerator morseCodeGenerator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reference_sheet, container, false);

        morseCodeGenerator = new MorseCodeGenerator(requireContext());
        TableLayout tableLayout = view.findViewById(R.id.table_layout);

        // 3-column layout logic
        int numColumns = 3; // Example number of columns
        int numRows = (int) Math.ceil((double) morseCodeGenerator.getTotalSymbols() / numColumns);
        int totalEntries = morseCodeGenerator.getTotalSymbols();

        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            TableRow tableRow = new TableRow(requireContext());
            tableRow.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
            ));

            for (int colIndex = 0; colIndex < numColumns; colIndex++) {
                int dataIndex = rowIndex + colIndex * numRows;
                if (dataIndex < totalEntries) {
                    String symbol = morseCodeGenerator.getCharacterByIndex(dataIndex);
                    String morseCode = morseCodeGenerator.getMorseCode(symbol);

                    // Create a button for each symbol
                    Button button = new Button(requireContext());
                    button.setText(symbol + " " + morseCode); // Remove parentheses
                    button.setSoundEffectsEnabled(false); // Disable click sound
                    button.setOnClickListener(v -> morseCodeGenerator.playMorseCode(symbol));

                    // Add button to the row
                    tableRow.addView(button);
                } else {
                    // Add empty space for missing entries
                    View emptySpace = new View(requireContext());
                    emptySpace.setLayoutParams(new TableRow.LayoutParams(
                            TableRow.LayoutParams.MATCH_PARENT,
                            TableRow.LayoutParams.WRAP_CONTENT,
                            1.0f
                    ));
                    tableRow.addView(emptySpace);
                }
            }


            // Add the completed row to the table
            tableLayout.addView(tableRow);
        }

        return view;
    }
}
