import Flutter
import UIKit
import ShazamKit

public class SwiftFlutterShazamKitPlugin: NSObject, FlutterPlugin {
    private var session: SHSession?
    private let audioEngine = AVAudioEngine()
    private let mixerNode = AVAudioMixerNode()
    private var callbackChannel: FlutterMethodChannel?
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_shazam_kit", binaryMessenger: registrar.messenger())
        let instance = SwiftFlutterShazamKitPlugin(callbackChannel: FlutterMethodChannel(name: "flutter_shazam_kit_callback", binaryMessenger: registrar.messenger()))
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    init(callbackChannel: FlutterMethodChannel? = nil) {
        self.callbackChannel = callbackChannel
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "configureShazamKitSession":
            configureShazamKitSession()
            result(nil)
        case "startDetectionWithMicrophone":
            do{
                try startListening(result: result)
            }catch{
                callbackChannel?.invokeMethod("didHasError", arguments: error.localizedDescription)
            }
        case "endDetectionWithMicrophone":
            do {
                try stopListening()
            } catch {
                callbackChannel?.invokeMethod("didHasError", arguments: "Detection end failed due to \(error)")
            }
            result(nil)
        case "pauseDetection":
            do {
                try pauseDetection()
            } catch {
                callbackChannel?.invokeMethod("didHasError", arguments: "Pause detection failed due to \(error)")
            }
        case "resumeDetection":
            do {
                try resumeDetection()
            } catch {
                callbackChannel?.invokeMethod("didHasError", arguments: "Resume detection failed due to \(error)")
            }
        case "endSession":
            session = nil
            result(nil)
        default:
            result(nil)
        }
    }
}

//MARK: ShazamKit session delegation here
//MARK: Methods for AVAudio
extension SwiftFlutterShazamKitPlugin{
    func configureShazamKitSession(){
        session = SHSession()
        session?.delegate = self
    }
    
    func prepareAudio() throws{
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode: .default, options: [.mixWithOthers, .defaultToSpeaker, .allowBluetoothA2DP])
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
    }
    
    private func generateSignature() {
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: .zero)
        
        inputNode.installTap(onBus: .zero, bufferSize: 1024, format: recordingFormat) { [weak session] buffer, _ in
            session?.matchStreamingBuffer(buffer, at: nil)
        }
    }
    
    private func startAudioRecording() throws {
        try audioEngine.start()
    }
    
    func startListening(result: FlutterResult) throws {
        guard session != nil else{
            callbackChannel?.invokeMethod("didHasError", arguments: "ShazamSession not found, please call configureShazamKitSession() first to initialize it.")
            result(nil)
            return
        }
        do {
            try prepareAudio()
        } catch {
            callbackChannel?.invokeMethod("didHasError", arguments: "Audio preparation failed due to \(error)")
            result(nil)
            return
        }
        generateSignature()
        do {
            try startAudioRecording()
        } catch {
            callbackChannel?.invokeMethod("didHasError", arguments: "Start audio recording failed due to \(error)")
            result(nil)
            return
        }
        
        callbackChannel?.invokeMethod("detectStateChanged", arguments: 1)
        
        result(nil)
    }
    
    func stopListening() throws {
        let audioSession = AVAudioSession.sharedInstance()
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        try audioSession.setActive(false)
        callbackChannel?.invokeMethod("detectStateChanged", arguments: 0)
    }
    
    func pauseDetection() throws {
        print("Audio engine is running at pause time? \(audioEngine.isRunning)")
        audioEngine.pause()
    }
    
    func resumeDetection() throws {
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setActive(true)
        try audioEngine.start()
        print("Audio engine is running after resuming? \(audioEngine.isRunning)")
        //        generateSignature()
    }
}

struct MediaItems: Encodable {
    let title: String?
    let subtitle: String?
    let shazamId: String?
    let appleMusicId: String?
    let appleMusicUrL: URL?
    let artworkUrl: URL?
    let artist: String?
    let matchOffset: TimeInterval
    let videoUrl: URL?
    let webUrl: URL?
    let genres: [String]
    let isrc: String?
    let explicitContent: Bool
    let url: URL?
    let albumTitle: String?
    let composerName: String?
    let releaseDate: String?
}

//MARK: Delegate methods for SHSession
extension SwiftFlutterShazamKitPlugin: SHSessionDelegate{
    public func session(_ session: SHSession, didFind match: SHMatch) {
        let mediaItems = match.mediaItems
        if let firstItem = mediaItems.first {
            let songs = firstItem.songs.first
            let url = songs?.previewAssets?.first?.url
            let albumTitle = songs?.albumTitle
            let composerName = songs?.composerName
            let releaseDate = songs?.releaseDate?.ISO8601Format()
            
            let _shazamMedia = MediaItems(
                title:firstItem.title,
                subtitle:firstItem.subtitle,
                shazamId:firstItem.shazamID,
                appleMusicId:firstItem.appleMusicID,
                appleMusicUrL:firstItem.appleMusicURL,
                artworkUrl:firstItem.artworkURL,
                artist:firstItem.artist,
                matchOffset:firstItem.matchOffset,
                videoUrl:firstItem.videoURL,
                webUrl:firstItem.webURL,
                genres:firstItem.genres,
                isrc:firstItem.isrc,
                explicitContent: firstItem.explicitContent,
                url: url,
                albumTitle: albumTitle,
                composerName: composerName,
                releaseDate: releaseDate
            )
            
            //            print("=== _shazamMedia", _shazamMedia)
            
            do {
                let jsonData = try JSONEncoder().encode([_shazamMedia])
                let jsonString = String(data: jsonData, encoding: .utf8)!
                self.callbackChannel?.invokeMethod("matchFound", arguments: jsonString)
            } catch {
                callbackChannel?.invokeMethod("didHasError", arguments: "Error when trying to format data, please try again")
            }
        }
    }
    
    public func session(_ session: SHSession, didNotFindMatchFor signature: SHSignature, error: Error?) {
        callbackChannel?.invokeMethod("notFound", arguments: nil)
        callbackChannel?.invokeMethod("didHasError", arguments: error?.localizedDescription)
    }
}

