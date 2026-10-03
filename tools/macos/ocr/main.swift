// llegeix-ocr: reads the words off a picture of a page with Apple's Vision.
//
// The Mac's counterpart of ML Kit's Latin recogniser on the phone: on the
// device, offline, free. A PNG arrives on stdin; the lines come back on stdout
// as JSON, each with its words, every box in the picture's own pixels with the
// origin at the top left:
//
//   [{"text": "...", "box": {"x":..,"y":..,"w":..,"h":..},
//     "words": [{"text": "...", "box": {...}}, ...]}, ...]
//
// Words are runs of anything but white space, as ML Kit's elements are, so
// "intel·ligència" and "l'aigua" stay whole and a full stop stays on its word.

import CoreGraphics
import Foundation
import ImageIO
import Vision

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

let input = FileHandle.standardInput.readDataToEndOfFile()
guard let source = CGImageSourceCreateWithData(input as CFData, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fail("not a picture")
}
let width = Double(image.width)
let height = Double(image.height)

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
// Correction against a dictionary helps only when the dictionary is Catalan's;
// any other one "corrects" Catalan words into its own.
request.usesLanguageCorrection = false
// Catalan first if this Mac's Vision reads it, then the Romance languages
// whose letters cover Catalan's: à è é í ï ò ó ú ü ç and the middle dot.
let wanted = ["ca-ES", "fr-FR", "it-IT", "es-ES", "pt-BR"]
if let supported = try? request.supportedRecognitionLanguages() {
    let chosen = wanted.filter { supported.contains($0) }
    if !chosen.isEmpty { request.recognitionLanguages = chosen }
}

do {
    try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
} catch {
    fail("recognition failed: \(error)")
}

/// Vision's normalised, bottom-left box as pixels from the top left.
func pixels(_ box: CGRect) -> [String: Double] {
    ["x": box.minX * width, "y": (1 - box.maxY) * height, "w": box.width * width, "h": box.height * height]
}

var lines: [[String: Any]] = []
for observation in request.results ?? [] {
    guard let candidate = observation.topCandidates(1).first else { continue }
    let text = candidate.string
    var words: [[String: Any]] = []
    var index = text.startIndex
    while index < text.endIndex {
        guard let start = text[index...].firstIndex(where: { !$0.isWhitespace }) else { break }
        let end = text[start...].firstIndex(where: { $0.isWhitespace }) ?? text.endIndex
        if let box = try? candidate.boundingBox(for: start..<end) {
            words.append(["text": String(text[start..<end]), "box": pixels(box.boundingBox)])
        }
        index = end
    }
    lines.append(["text": text, "box": pixels(observation.boundingBox), "words": words])
}

let output = try JSONSerialization.data(withJSONObject: lines)
FileHandle.standardOutput.write(output)
