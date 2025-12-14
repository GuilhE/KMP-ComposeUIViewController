#!/bin/bash

# DEFAULT VALUES
kmp_module="shared"
iosApp_project_folder="iosApp"
iosApp_name="iosApp"
iosApp_target_name="iosApp"
group_name="Representables"
#####################

xcodeproj_path="$iosApp_name.xcodeproj"
files_source="$kmp_module/build/generated/ksp/"
files_destination="$iosApp_project_folder/$group_name/"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../../" && pwd )"
IOS_APP_DIR="$PROJECT_ROOT/$iosApp_project_folder"

install_gem() {
  local gem_name="$1"
  local version_constraint="$2"

  local install_output
  local install_result

  install_output=$(gem install "$gem_name" -v "$version_constraint" 2>&1)
  install_result=$?

  if [ $install_result -ne 0 ]; then
    if echo "$install_output" | grep -q "FilePermissionError\|don't have write permissions"; then
      echo "> Permission denied. Installing to user directory..."
      install_output=$(gem install "$gem_name" -v "$version_constraint" --user-install 2>&1)
      install_result=$?

      if [ $install_result -eq 0 ]; then
        local user_gem_bin="$(ruby -r rubygems -e 'puts Gem.user_dir')/bin"
        if [ -d "$user_gem_bin" ] && [[ ":$PATH:" != *":$user_gem_bin:"* ]]; then
          export PATH="$user_gem_bin:$PATH"
          echo "> Added $user_gem_bin to PATH"
        fi
      else
        echo "> ERROR: Failed to install gem even with --user-install"
        echo "$install_output"
        echo ""
        echo "Please try manually with one of these commands:"
        echo "  gem install $gem_name --user-install"
        echo "  sudo gem install $gem_name"
        exit 1
      fi
    else
      echo "> ERROR: Failed to install gem"
      echo "$install_output"
      exit 1
    fi
  fi

  echo "$install_output" | grep -v "pristine" | grep -v "RI documentation"
}

check_for_xcodeproj() {
  # Minimum version required for PBXFileSystemSynchronizedRootGroup support
  local min_version="1.27.0"

  local current_version
  current_version=$(gem list xcodeproj --local 2>/dev/null | grep "^xcodeproj " | sed 's/.*(\([0-9.]*\).*/\1/')

  if [ -z "$current_version" ]; then
    echo "> Installing 'xcodeproj' gem (minimum version: $min_version)..."
    install_gem "xcodeproj" ">= $min_version"
  else
    if [ "$(printf '%s\n' "$min_version" "$current_version" | sort -V | head -n1)" != "$min_version" ]; then
      echo "> Current xcodeproj version ($current_version) is below minimum required ($min_version)"
      echo "> Updating xcodeproj gem..."
      install_gem "xcodeproj" ">= $min_version"
    else
      echo "> Using xcodeproj gem version $current_version (minimum: $min_version)"
    fi
  fi

  # Verify installation was successful
  if ! gem spec xcodeproj > /dev/null 2>&1; then
    echo "> ERROR: Failed to install/verify xcodeproj gem"
    echo "> Try manually with one of these commands:"
    echo "  gem install xcodeproj --user-install"
    echo "  sudo gem install xcodeproj"
    exit 1
  fi
}

clean_and_copy_files() {
  local files_source="$1"
  local files_destination="$2"

  echo "> Cleaning destination folder: $files_destination"
  rm -rf "$files_destination"
  mkdir -p "$files_destination"

  local files_count=0
  while IFS= read -r -d '' file; do
    rsync -a "$file" "$files_destination"
    ((files_count++))
  done < <(find "$files_source" -type f -name '*.swift' -print0)

  echo "> Copied $files_count file(s)"
  return $files_count
}

rebuild_file_references() {
  local xcodeproj_path="$1"
  local iosApp_target_name="$2"
  local group_name="$3"

  ruby_script='
    require "xcodeproj"

    xcodeproj_path = ARGV[0]
    iosApp_target_name = ARGV[1]
    group_name = ARGV[2]

    files_to_add = Dir.glob("#{group_name}/*.swift")
    if files_to_add.empty?
      puts "> No Swift files found. Skipping references"
      exit
    end

    begin
      xcodeproj = Xcodeproj::Project.open(xcodeproj_path)
    rescue => e
      puts "> ERROR: Failed to open Xcode project: #{e.message}"
      puts "> This may be due to an incompatible Xcode project format."
      puts "> Try updating the xcodeproj gem: gem install xcodeproj"
      exit 1
    end

    target = xcodeproj.targets.find { |t| t.name == iosApp_target_name }

    unless target
      puts "> ERROR: Target \"#{iosApp_target_name}\" not found"
      exit 1
    end

    # Remove existing group and its references
    existing_group = xcodeproj[group_name]
    if existing_group
      puts "> Removing existing group \"#{group_name}\" and its references"
      existing_group.clear
      existing_group.remove_from_project
    end

    # Create new group
    group = xcodeproj.new_group(group_name)
    puts "> Created new group \"#{group_name}\""

    # Add all files to the group and target
    files_to_add.each do |file_path|
      file_reference = group.new_file(file_path)
      target.add_file_references([file_reference])
      puts "> Added: #{File.basename(file_path)}"
    end

    begin
      xcodeproj.save
      puts "> Xcodeproj saved successfully"
    rescue => e
      puts "> ERROR: Failed to save Xcode project: #{e.message}"
      exit 1
    end
  '
  ruby -e "$ruby_script" "$xcodeproj_path" "$iosApp_target_name" "$group_name"
}

check_for_xcodeproj
echo "> Starting clean rebuild process"
clean_and_copy_files "$files_source" "$files_destination"
files_copied=$?

if [ "$files_copied" -gt 0 ]; then
  echo "> Rebuilding Xcode references"
  cd "$IOS_APP_DIR" || exit
  rebuild_file_references "$xcodeproj_path" "$iosApp_target_name" "$group_name"
  echo "> Done"
else
  echo "> No files to process. Exiting"
fi