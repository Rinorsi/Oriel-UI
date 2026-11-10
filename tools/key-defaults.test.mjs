import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('Home defaults survive focus refresh in player and development environments on GLFW and SDL', () => {
  assert.ok(process.env.ORIEL_TEST_TMP, 'Set ORIEL_TEST_TMP to workspace daily scratch');
  const dir = fs.mkdtempSync(path.join(process.env.ORIEL_TEST_TMP, 'key-defaults-'));
  const bin = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name) : name;
  // Compile the actual initializer against a small input-backend contract. SDL indexes
  // a direct keyboard buffer; GLFW and SDL use different UNKNOWN and F8 values.
  const sources = {
    'com/mojang/blaze3d/platform/InputConstants.java': `
      package com.mojang.blaze3d.platform;
      public final class InputConstants {
        public record Key(int value) { public int getValue() { return value; } }
        public static final Key UNKNOWN = new Key(Integer.getInteger("unknown"));
        public static final int KEY_F8 = Integer.getInteger("f8");
      }`,
    'net/minecraft/client/KeyMapping.java': `
      package net.minecraft.client;
      public final class KeyMapping {
        public final int value;
        public KeyMapping(String name, int value, Category category) { this.value = value; }
        public boolean consumeClick() { return false; }
        public static final class Category {
          public static Category register(Object id) { return new Category(); }
        }
      }`,
    'dev/oriel/mod/internal/platform/Services.java': `
      package dev.oriel.mod.internal.platform;
      public final class Services {
        public static final Services PLATFORM = new Services();
        public boolean isDevelopmentEnvironment() { return Boolean.getBoolean("development"); }
      }`,
    'dev/oriel/mod/internal/Constants.java': `
      package dev.oriel.mod.internal;
      public final class Constants { public static Object id(String id) { return id; } }`,
    'dev/oriel/mod/client/OrielScreens.java': `
      package dev.oriel.mod.client;
      public final class OrielScreens { public static void open(Object page) {} }`,
    'dev/oriel/kit/OrielKit.java': 'package dev.oriel.kit; public final class OrielKit {}',
    'dev/oriel/kit/ReferenceContent.java': `
      package dev.oriel.kit;
      public final class ReferenceContent { public static Object home() { return null; } }`,
    'FocusProbe.java': `
      import com.mojang.blaze3d.platform.InputConstants;
      import dev.oriel.mod.internal.client.OrielKeys;
      public final class FocusProbe {
        public static void main(String[] args) {
          int key = OrielKeys.HOME.value;
          int unknown = InputConstants.UNKNOWN.getValue();
          // KeyMapping.setAll skips only the current backend's UNKNOWN sentinel.
          if (key != unknown) java.nio.ByteBuffer.allocateDirect(512).get(key);
          int expected = Boolean.getBoolean("development") ? InputConstants.KEY_F8 : unknown;
          if (key != expected) throw new AssertionError("default=" + key + " expected=" + expected);
          System.out.println("PASS default=" + key);
        }
      }`
  };
  const files = Object.entries(sources).map(([name, source]) => {
    const file = path.join(dir, name);
    fs.mkdirSync(path.dirname(file), {recursive:true});
    fs.writeFileSync(file, source);
    return file;
  });
  execFileSync(bin('javac'), ['-encoding', 'UTF-8', '-d', dir,
    path.join(root, 'common/src/main/java/dev/oriel/mod/internal/client/OrielKeys.java'), ...files]);
  for (const [version, unknown, f8] of [['26.1.2', -1, 297], ['26.2', -1, 297], ['26.3', 0, 65]]) {
    for (const development of [false, true]) {
      const output = execFileSync(bin('java'), [`-Dunknown=${unknown}`, `-Df8=${f8}`,
        `-Ddevelopment=${development}`, '-cp', dir, 'FocusProbe'], {encoding:'utf8'});
      assert.match(output, /PASS default=/, `${version} development=${development}`);
    }
  }
});
