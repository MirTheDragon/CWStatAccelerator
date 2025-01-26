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

        // List of all symbols with their Morse codes
        List<String[]> morseData = new ArrayList<>();
        morseData.add(new String[]{"A", ".-"}); morseData.add(new String[]{"B", "-..."}); morseData.add(new String[]{"C", "-.-."});
        morseData.add(new String[]{"D", "-.."}); morseData.add(new String[]{"E", "."}); morseData.add(new String[]{"F", "..-."});
        morseData.add(new String[]{"G", "--."}); morseData.add(new String[]{"H", "...."}); morseData.add(new String[]{"I", ".."});
        morseData.add(new String[]{"J", ".---"}); morseData.add(new String[]{"K", "-.-"}); morseData.add(new String[]{"L", ".-.."});
        morseData.add(new String[]{"M", "--"}); morseData.add(new String[]{"N", "-."}); morseData.add(new String[]{"O", "---"});
        morseData.add(new String[]{"P", ".--."}); morseData.add(new String[]{"Q", "--.-"}); morseData.add(new String[]{"R", ".-."});
        morseData.add(new String[]{"S", "..."}); morseData.add(new String[]{"T", "-"}); morseData.add(new String[]{"U", "..-"});
        morseData.add(new String[]{"V", "...-"}); morseData.add(new String[]{"W", ".--"}); morseData.add(new String[]{"X", "-..-"});
        morseData.add(new String[]{"Y", "-.--"}); morseData.add(new String[]{"Z", "--.."});
        morseData.add(new String[]{"0", "-----"}); morseData.add(new String[]{"1", ".----"}); morseData.add(new String[]{"2", "..---"});
        morseData.add(new String[]{"3", "...--"}); morseData.add(new String[]{"4", "....-"}); morseData.add(new String[]{"5", "....."});
        morseData.add(new String[]{"6", "-...."}); morseData.add(new String[]{"7", "--..."}); morseData.add(new String[]{"8", "---.."});
        morseData.add(new String[]{"9", "----."});
        morseData.add(new String[]{"?", "..--.."}); morseData.add(new String[]{"!", "-.-.--"}); morseData.add(new String[]{".", ".-.-.-"});
        morseData.add(new String[]{",", "--..--"}); morseData.add(new String[]{";", "-.-.-."}); morseData.add(new String[]{":", "---..."});
        morseData.add(new String[]{"+", ".-.-."}); morseData.add(new String[]{"-", "-....-"}); morseData.add(new String[]{"/", "-..-."});
        morseData.add(new String[]{"=", "-...-"});

        // 3-column layout logic
        int numRows = 15; // Maximum number of rows
        int numColumns = 3; // Number of columns
        int totalEntries = morseData.size();

        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            TableRow tableRow = new TableRow(requireContext());
            tableRow.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
            ));

            for (int colIndex = 0; colIndex < numColumns; colIndex++) {
                int dataIndex = rowIndex + colIndex * numRows;
                if (dataIndex < totalEntries) {
                    String symbol = morseData.get(dataIndex)[0];
                    String morseCode = morseData.get(dataIndex)[1];

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
