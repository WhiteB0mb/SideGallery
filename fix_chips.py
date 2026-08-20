import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

# Replace all occurrences of the AssistChip rows for dimensions
pattern = r"""                                    Row\(
                                        modifier = Modifier\.fillMaxWidth\(\),
                                        horizontalArrangement = Arrangement\.spacedBy\(6\.dp\)
                                    \) \{
                                        listOf\([^)]+\)\.forEach \{ \(value, label\) ->
                                            AssistChip\(
                                                onClick = \{ viewModel\.setPanel(Height|Width|Opacity)Percent\(value\) \},
                                                label = \{ Text\(label, style = MaterialTheme\.typography\.labelSmall, maxLines = 1\) \},
                                                colors = if \(panel(Height|Width|Opacity)Percent == value\)
                                                    AssistChipDefaults\.assistChipColors\(containerColor = MaterialTheme\.colorScheme\.primaryContainer\)
                                                else
                                                    AssistChipDefaults\.assistChipColors\(\),
                                                modifier = Modifier\.weight\(1f\)
                                            \)
                                        \}
                                    \}"""

new_code = re.sub(pattern, "", code)

# Also replace for MainScreen without the heavy indentation (or just arbitrary whitespace)
pattern2 = r"""\s+Row\(
\s+modifier = Modifier\.fillMaxWidth\(\),
\s+horizontalArrangement = Arrangement\.spacedBy\(6\.dp\)
\s+\) \{
\s+listOf\([^)]+\)\.forEach \{ \(value, label\) ->
\s+AssistChip\(
\s+onClick = \{ viewModel\.setPanel(Height|Width|Opacity)Percent\(value\) \},
\s+label = \{ Text\(label, style = MaterialTheme\.typography\.labelSmall, maxLines = 1\) \},
\s+colors = if \(panel(Height|Width|Opacity)Percent == value\)
\s+AssistChipDefaults\.assistChipColors\(containerColor = MaterialTheme\.colorScheme\.primaryContainer\)
\s+else
\s+AssistChipDefaults\.assistChipColors\(\),
\s+modifier = Modifier\.weight\(1f\)
\s+\)
\s+\}
\s+\}"""

new_code = re.sub(pattern2, "", code)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(new_code)
