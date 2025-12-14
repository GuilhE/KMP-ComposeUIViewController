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
      echo "  > Permission denied. Installing to user directory..."
      install_output=$(gem install "$gem_name" -v "$version_constraint" --user-install 2>&1)
      install_result=$?

      if [ $install_result -eq 0 ]; then
        local user_gem_bin="$(ruby -r rubygems -e 'puts Gem.user_dir')/bin"
        if [ -d "$user_gem_bin" ] && [[ ":$PATH:" != *":$user_gem_bin:"* ]]; then
          export PATH="$user_gem_bin:$PATH"
          echo "  >Added $user_gem_bin to PATH"
        fi
      else
        echo "  > ERROR: Failed to install gem even with --user-install"
        echo "$install_output"
        echo ""
        echo "  Please try manually with one of these commands:"
        echo "    gem install $gem_name --user-install"
        echo "    sudo gem install $gem_name"
        exit 1
      fi
    else
      echo "  > ERROR: Failed to install gem"
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
    echo "  > Installing 'xcodeproj' gem (minimum version: $min_version)..."
    install_gem "xcodeproj" ">= $min_version"
  else
    if [ "$(printf '%s\n' "$min_version" "$current_version" | sort -V | head -n1)" != "$min_version" ]; then
      echo "  > Current xcodeproj version ($current_version) is below minimum required ($min_version)"
      echo "  > Updating xcodeproj gem..."
      install_gem "xcodeproj" ">= $min_version"
    else
      echo "  > Using xcodeproj gem version $current_version (minimum: $min_version)"
    fi
  fi

  # Verify installation was successful
  if ! gem spec xcodeproj > /dev/null 2>&1; then
    echo "  > ERROR: Failed to install/verify xcodeproj gem"
    echo "  > Try manually with one of these commands:"
    echo "    gem install xcodeproj --user-install"
    echo "    sudo gem install xcodeproj"
    exit 1
  fi
}

smart_sync_files() {
  local files_source="$1"
  local files_destination="$2"

  mkdir -p "$files_destination"

  local files_copied=0
  local files_unchanged=0
  local files_removed=0
  local has_changes=0

  local source_files_list=$(mktemp)
  local source_files_map=$(mktemp)
  trap "rm -f $source_files_list $source_files_map" RETURN

  # First pass: find all Swift files and deduplicate by basename (keeping newest)
  while IFS= read -r -d '' source_file; do
    local filename=$(basename "$source_file")
    local existing_file=$(grep "^$filename|" "$source_files_map" 2>/dev/null | cut -d'|' -f2)

    if [ -z "$existing_file" ]; then
      # First occurrence of this filename
      echo "$filename|$source_file" >> "$source_files_map"
    else
      # File with same name exists, keep the newer one
      if [ "$source_file" -nt "$existing_file" ]; then
        # Remove old entry and add new one
        sed -i '' "/^$filename|/d" "$source_files_map"
        echo "$filename|$source_file" >> "$source_files_map"
      fi
    fi
  done < <(find "$files_source" -type f -name '*.swift' -print0)

  # Second pass: sync files
  while IFS='|' read -r filename source_file; do
    local dest_file="$files_destination/$filename"

    echo "$filename" >> "$source_files_list"

    local should_copy=0
    if [ ! -f "$dest_file" ]; then
      should_copy=1
      echo "  > New file: $filename"
    else
      local source_md5=$(md5 -q "$source_file" 2>/dev/null || md5sum "$source_file" | cut -d' ' -f1)
      local dest_md5=$(md5 -q "$dest_file" 2>/dev/null || md5sum "$dest_file" | cut -d' ' -f1)

      if [ "$source_md5" != "$dest_md5" ]; then
        should_copy=1
        echo "  > Modified: $filename"
      else
        files_unchanged=$((files_unchanged + 1))
      fi
    fi

    if [ $should_copy -eq 1 ]; then
      cp "$source_file" "$dest_file"
      files_copied=$((files_copied + 1))
      has_changes=1
    fi
  done < "$source_files_map"

  if [ -d "$files_destination" ]; then
    while IFS= read -r -d '' dest_file; do
      local filename=$(basename "$dest_file")
      if ! grep -Fxq "$filename" "$source_files_list"; then
        echo "  > Removed: $filename"
        rm -f "$dest_file"
        files_removed=$((files_removed + 1))
        has_changes=1
      fi
    done < <(find "$files_destination" -type f -name '*.swift' -print0)
  fi

  echo "  > Summary: $files_unchanged unchanged, $files_copied copied, $files_removed removed"

  # Return 0 if no changes, 1 if there were changes
  return $has_changes
}

rebuild_file_references() {
  local xcodeproj_path="$1"
  local iosApp_target_name="$2"
  local group_name="$3"
  local force_rebuild="${4:-false}"

  ruby_script='
    require "xcodeproj"

    xcodeproj_path = ARGV[0]
    iosApp_target_name = ARGV[1]
    group_name = ARGV[2]
    force_rebuild = ARGV[3] == "true"

    files_to_add = Dir.glob("#{group_name}/*.swift").sort
    if files_to_add.empty?
      puts "  > No Swift files found. Skipping references"
      exit
    end

    begin
      xcodeproj = Xcodeproj::Project.open(xcodeproj_path)
    rescue => e
      puts "  > ERROR: Failed to open Xcode project: #{e.message}"
      puts "  > This may be due to an incompatible Xcode project format."
      puts "  > Try updating the xcodeproj gem: gem install xcodeproj"
      exit 1
    end

    target = xcodeproj.targets.find { |t| t.name == iosApp_target_name }

    unless target
      puts "  > ERROR: Target \"#{iosApp_target_name}\" not found"
      exit 1
    end

    unless force_rebuild
      existing_group = xcodeproj[group_name]
      if existing_group
        existing_files = existing_group.files.map { |f| File.basename(f.path) }.compact.sort
        files_basenames = files_to_add.map { |f| File.basename(f) }.sort

        if existing_files == files_basenames
          puts "  > References are already up to date. Skipping rebuild"
          exit 0
        else
          puts "  > Detected differences in file references"
          puts "  >   Old: #{existing_files.inspect}"
          puts "  >   New: #{files_basenames.inspect}"
        end
      end
    end

    group = xcodeproj[group_name]
    if group.nil?
      group = xcodeproj.new_group(group_name)
      puts "  > Created new group \"#{group_name}\""
    end

    existing_file_refs = {}
    group.files.each do |file_ref|
      basename = File.basename(file_ref.path)
      existing_file_refs[basename] = file_ref
    end

    expected_files = {}
    files_to_add.each do |file_path|
      basename = File.basename(file_path)
      expected_files[basename] = file_path
    end

    files_removed = 0
    existing_file_refs.each do |basename, file_ref|
      unless expected_files.key?(basename)
        puts "  > Removing: #{basename}"
        target.source_build_phase.remove_file_reference(file_ref)
        file_ref.remove_from_project
        files_removed += 1
      end
    end

    files_added = 0
    expected_files.each do |basename, file_path|
      unless existing_file_refs.key?(basename)
        puts "  > Adding: #{basename}"
        file_reference = group.new_file(file_path)
        target.add_file_references([file_reference])
        files_added += 1
      end
    end

    puts "  > Summary: #{files_added} added, #{files_removed} removed, #{existing_file_refs.size - files_removed} unchanged"

    begin
      xcodeproj.save
      puts "  > Xcodeproj saved successfully"
    rescue => e
      puts "  > ERROR: Failed to save Xcode project: #{e.message}"
      exit 1
    end
  '
  ruby -e "$ruby_script" "$xcodeproj_path" "$iosApp_target_name" "$group_name" "$force_rebuild"
}

check_for_xcodeproj
echo "  > Starting smart sync process"
smart_sync_files "$files_source" "$files_destination"
has_changes=$?

if [ $has_changes -eq 1 ]; then
  echo "  > Detected changes. Rebuilding Xcode references"
  cd "$IOS_APP_DIR" || exit
  rebuild_file_references "$xcodeproj_path" "$iosApp_target_name" "$group_name" "false"
  echo "  > Done"
else
  echo "  > No changes detected. Verifying Xcode references are up to date"
  cd "$IOS_APP_DIR" || exit
  rebuild_file_references "$xcodeproj_path" "$iosApp_target_name" "$group_name" "false"
  echo "  > Done"
fi