import SwiftUI

private let availableColors: [Color] = [.red, .green, .blue, .yellow, .orange, .purple]

func getRandomColors() -> [Color] {
    return Array(availableColors.shuffled().prefix(2))
}

extension Color {
    var asInt64: Int64 {
        let uiColor = UIColor(self)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        
        uiColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        
        let redValue = Int64(red * 255)
        let greenValue = Int64(green * 255)
        let blueValue = Int64(blue * 255)
        let alphaValue = Int64(alpha * 255)
        
        return (alphaValue << 24) | (redValue << 16) | (greenValue << 8) | blueValue
    }
    
    init(argb: Int64) {
        let alpha = Double((argb >> 24) & 0xFF) / 255.0
        let red = Double((argb >> 16) & 0xFF) / 255.0
        let green = Double((argb >> 8) & 0xFF) / 255.0
        let blue = Double(argb & 0xFF) / 255.0
        
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}

private let dateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "dd-MM-yyyy HH:mm:ss"
    return formatter
}()

private func dateFromMilliseconds(milliseconds: Int64) -> Date {
    let timeInterval = TimeInterval(milliseconds) / 1000.0
    return Date(timeIntervalSince1970: timeInterval)
}

func printMilis() {
    let millis = Int64(Date().timeIntervalSince1970 * 1000)
    print("Shuffled at \(dateFormatter.string(from: dateFromMilliseconds(milliseconds: Int64(truncating: millis as NSNumber))))", terminator: "\n")
}
