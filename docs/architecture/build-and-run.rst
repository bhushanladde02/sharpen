Build and run
=============

Prerequisites
-------------

* JDK 21 (the project compiles with ``--release 21``; a newer JDK such as 26 can run the build but the
  IntelliJ project SDK should be a 21 for Spring Boot 3.3 tooling to behave — set it under *Project Structure*).
* Maven 3.9 with a populated local repository (the dependency set is identical to the recovery-routes project,
  so no new downloads are needed on the same machine).
* Optional: Docker for the PostgreSQL shape; Python 3 with Sphinx for these docs.

IntelliJ IDEA
-------------

Open the ``sharpen`` folder. Shared run configurations in ``.run/`` appear in the run dropdown:

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Configuration
     - Purpose
   * - Sharpen (dev, H2)
     - Start the app on http://localhost:8080 with H2 in ``./data`` and demo accounts seeded.
   * - Sharpen (postgres)
     - Same app with the ``postgres`` profile against a local database ``sharpen`` / ``sharpen``.
   * - Sharpen (Application)
     - Plain JVM run configuration; works in Community Edition.
   * - Maven package (build + tests)
     - ``mvn clean package`` → ``target/sharpen.jar``.
   * - All tests
     - JUnit run of ``io.sharpen``.

If IntelliJ cannot resolve ``spring-boot-starter-parent:3.3.5``, point *Settings → Build Tools → Maven* at your
own ``~/.m2/repository`` rather than a bundled Maven with an empty repository.

Command line
------------

.. code-block:: bash

   mvn clean package          # build + tests
   mvn spring-boot:run        # dev, H2, demo data
   java -jar target/sharpen.jar
   SPRING_PROFILES_ACTIVE=postgres SHARPEN_DB_URL=jdbc:postgresql://localhost:5432/sharpen \
     SHARPEN_DB_USER=sharpen SHARPEN_DB_PASSWORD=sharpen java -jar target/sharpen.jar
   docker compose up --build  # PostgreSQL + app; schema applied on first start

Demo accounts (password ``demo1234``): ``demo@sharpen.io``, ``lena@sharpen.io``, ``marcus@sharpen.io``
(individuals) and ``hiring@sharpen.io`` (company).

Configuration
-------------

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Property / variable
     - Meaning
   * - ``sharpen.demo-data``
     - Seed demo accounts on start when true (default true in dev, ``SHARPEN_DEMO_DATA`` in postgres profile).
   * - ``SHARPEN_DB_URL`` / ``_USER`` / ``_PASSWORD``
     - PostgreSQL connection for the ``postgres`` profile.
   * - ``server.port``
     - Default 8080.
   * - ``spring.servlet.multipart.max-file-size``
     - Upload cap, default 10 MB.

Documentation
-------------

.. code-block:: bash

   cd docs && pip install -r requirements.txt && make html
   open _build/html/index.html
