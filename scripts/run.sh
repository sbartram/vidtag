#!/usr/bin/env bash
# run.sh

tag_ai='PLlQSMwNu3mELEgEnE2O7oL74UQls2r3x4&pp=sAgC'
tag_golf='PLlQSMwNu3mELs3zOFANjQmRQLpI6vO5MK'
tag_science='PLlQSMwNu3mELXK0RkVTkEeZmg4JxOk1h_'
tag_finance='PLlQSMwNu3mELhveEWgz8SDIWn_xZLZOq8&pp=sAgC'

export VIDTAG_YT_PLAYLIST_IDS="${tag_ai},${tag_golf},${tag_science},${tag_finance}"
echo "starting app"
./gradlew bootRun --args='--spring.profiles.active=local' | tee tmp/run.log

