package com.commonplace.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class WindowsOcrService implements OcrService {

    private static final long AVAILABILITY_TIMEOUT_SECONDS = 8;
    private static final long OCR_TIMEOUT_SECONDS = 45;

    @Override
    public boolean isAvailable() {
        if (!isWindows()) {
            return false;
        }

        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    availabilityScript()
            )
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(AVAILABILITY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public OcrResult extractTextFromImage(Path imagePath) {
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            return new OcrResult(
                    "",
                    List.of(new ImportIssue(ImportIssueSeverity.WARNING, "OCR image file was unavailable."))
            );
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    ocrScript()
            ).redirectErrorStream(true);

            processBuilder.environment().put(
                    "COMMONPLACE_OCR_IMAGE",
                    imagePath.toAbsolutePath().normalize().toString()
            );

            Process process = processBuilder.start();

            boolean completed = process.waitFor(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();

            if (!completed) {
                process.destroyForcibly();
                return new OcrResult(
                        "",
                        List.of(new ImportIssue(ImportIssueSeverity.WARNING, "Windows OCR timed out."))
                );
            }

            if (process.exitValue() != 0) {
                return new OcrResult(
                        "",
                        List.of(new ImportIssue(
                                ImportIssueSeverity.WARNING,
                                "Windows OCR could not read the page image: " + output
                        ))
                );
            }

            return new OcrResult(output, List.of());

        } catch (IOException e) {
            return new OcrResult(
                    "",
                    List.of(new ImportIssue(
                            ImportIssueSeverity.WARNING,
                            "Windows OCR failed: " + e.getMessage()
                    ))
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OcrResult(
                    "",
                    List.of(new ImportIssue(
                            ImportIssueSeverity.WARNING,
                            "Windows OCR failed: " + e.getMessage()
                    ))
            );
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private String availabilityScript() {
        return """
                $ErrorActionPreference = 'Stop'
                $null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
                if ([Windows.Media.Ocr.OcrEngine]::AvailableRecognizerLanguages.Count -le 0) { exit 1 }
                'available'
                """;
    }

    private String ocrScript() {
        return """
                $ErrorActionPreference = 'Stop'
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                $OutputEncoding = [System.Text.Encoding]::UTF8
                $ImagePath = $env:COMMONPLACE_OCR_IMAGE
                if ([string]::IsNullOrWhiteSpace($ImagePath)) { throw 'OCR image path was not provided.' }
                Add-Type -AssemblyName System.Runtime.WindowsRuntime
                $null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
                $null = [Windows.Storage.FileAccessMode, Windows.Storage, ContentType = WindowsRuntime]
                $null = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
                $null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
                $null = [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
                $null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
                $null = [Windows.Media.Ocr.OcrResult, Windows.Foundation, ContentType = WindowsRuntime]
                $asTaskMethods = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1
                }
                function Await-WinRt($Operation, [Type]$ResultType) {
                    $method = $asTaskMethods | Where-Object {
                        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
                    } | Select-Object -First 1
                    $task = $method.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
                    $task.Wait()
                    return $task.Result
                }
                $file = Await-WinRt ([Windows.Storage.StorageFile]::GetFileFromPathAsync($ImagePath)) ([Windows.Storage.StorageFile])
                $stream = Await-WinRt ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
                try {
                    $decoder = Await-WinRt ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
                    $bitmap = Await-WinRt ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
                    $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
                    if ($null -eq $engine) { throw 'No Windows OCR language is available.' }
                    $result = Await-WinRt ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
                    $result.Text
                } finally {
                    if ($stream -ne $null) { $stream.Dispose() }
                }
                """;
    }
}
