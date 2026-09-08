Sharpen — requirements and design
=================================

Sharpen is a web portal that tracks how a person works with AI, at work and at home, turns that into a
monthly report and an **AI score**, and publishes an AI-only professional profile that companies can browse.

This documentation is the single source of truth for *what* the product must do (requirements) and *how* the
prototype does it (architecture). Requirements are numbered so that tests, issues and commits can reference
them (``FR-12``, ``NFR-3``).

.. toctree::
   :maxdepth: 2
   :caption: Requirements

   requirements/01-vision
   requirements/02-personas
   requirements/03-functional
   requirements/04-scoring
   requirements/05-monthly-report
   requirements/06-data-ingestion
   requirements/07-profile-and-companies
   requirements/08-api
   requirements/09-security-privacy
   requirements/10-nonfunctional

.. toctree::
   :maxdepth: 2
   :caption: Design

   architecture/how-it-works
   architecture/overview
   architecture/data-model
   architecture/build-and-run

.. toctree::
   :maxdepth: 2
   :caption: Deployment (free, on Oracle Cloud)

   deployment/01-what-we-are-doing
   deployment/02-one-time-setup
   deployment/03-getting-a-server
   deployment/04-deploying
   deployment/05-day-two
   deployment/06-cheat-sheet

.. toctree::
   :maxdepth: 1
   :caption: Planning

   roadmap
   glossary

Building this documentation
---------------------------

.. code-block:: bash

   cd docs
   pip install -r requirements.txt
   make html            # output in docs/_build/html/index.html

IntelliJ renders ``.rst`` files with the bundled *ReStructuredText* plugin; enable it under
*Settings → Plugins* if the preview tab does not appear.
