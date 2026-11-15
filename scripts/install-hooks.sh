#!/bin/sh
# Run the privacy check before every commit.
git config core.hooksPath .githooks && echo "Pre-commit privacy check installed."
