# Sphinx configuration for the Sharpen documentation.
project = "Sharpen"
author = "Bhushan Ladde"
copyright = "2026, Bhushan Ladde"
release = "0.1.0"

extensions = ["sphinx.ext.todo", "sphinx.ext.autosectionlabel"]
autosectionlabel_prefix_document = True
todo_include_todos = True

templates_path = ["_templates"]
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store", "samples"]

html_theme = "sphinx_rtd_theme"
html_static_path = ["_static"]
html_title = "Sharpen — requirements and design"
