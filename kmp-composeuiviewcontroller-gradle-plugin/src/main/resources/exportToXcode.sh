#!/bin/bash

# DEFAULT VALUES
kmp_module="shared"
iosApp_project_folder="iosApp"
iosApp_name="iosApp"
iosApp_target_name="iosApp"
#####################

xcodeproj_path="$iosApp_name.xcodeproj"
files_source="$kmp_module/build/generated/ksp/"
group_name="Representables"
files_destination="$iosApp_project_folder/$group_name/"
copied_count=0
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../../" && pwd )"
IOS_APP_DIR="$PROJECT_ROOT/$iosApp_project_folder"

check_for_xcodeproj() {
  if ! gem spec xcodeproj > /dev/null 2>&1; then
    echo "Installing 'xcodeproj' gem..."
    gem install xcodeproj
  fi
}

copy_files() {
  local files_source="$1"
  local files_destination="$2"

  while IFS= read -r -d '' file; do
    destination_file="$files_destination/$(basename "$file")"
    if [ ! -f "$destination_file" ] || ! cmp -s "$file" "$destination_file"; then
      rsync -a --checksum "$file" "$files_destination"
      files_copied+=("$file")
    fi
  done < <(find "$files_source" -type f -name '*.swift' -print0)
  copied_count=${#files_copied[@]}
}

add_file_references() {
  local xcodeproj_path="$1"
  local iosApp_target_name="$2"
  local group_name="$3"

  ruby_script='
    require "xcodeproj"

    xcodeproj_path = ARGV[0]
    iosApp_target_name = ARGV[1]
    group_name = ARGV[2]

    files_to_copy = Dir.glob("#{group_name}/*")
    if files_to_copy.empty?
      puts "> No files to copy. Exiting"
      exit
    end

    xcodeproj = Xcodeproj::Project.open(xcodeproj_path)
    group = xcodeproj[group_name] || xcodeproj.new_group(group_name)
    target = xcodeproj.targets.find { |t| t.name == iosApp_target_name }

    existing_file_references = target.source_build_phase.files_references
    files_added = false

    files_to_copy.each do |file_path|
      unless existing_file_references.any? { |file_ref| file_ref.path == file_path }
        file_reference = group.new_file(file_path)
        puts "> #{file_reference} added!"
        target.add_file_references([file_reference])
        files_added = true
      end
    end

    unless files_added
      puts "> No new references to copy"
    end

    xcodeproj.save
  '
  ruby -e "$ruby_script" "$xcodeproj_path" "$iosApp_target_name" "$group_name"
}

check_for_xcodeproj
echo "> Copying files to $files_destination"
copy_files "$files_source" "$files_destination"
echo "> Checking for new references to be added to xcodeproj"
if [ "$copied_count" -gt 0 ]; then
  echo "> $copied_count new references detected"
  cd "$IOS_APP_DIR" || exit
  add_file_references "$xcodeproj_path" "$iosApp_target_name" "$group_name"
  echo "> Done"
else
  echo "> No new references detected"
fi